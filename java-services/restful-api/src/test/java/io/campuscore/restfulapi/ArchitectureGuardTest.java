package io.campuscore.restfulapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Dependency-free layering guard. Scans compiled {@code .class} files for
 * package placement and constant-pool references between layer packages.
 * Every tolerated violation is listed explicitly below.
 */
class ArchitectureGuardTest {

    private static final String ROOT = "io/campuscore/restfulapi";
    private static final Pattern CLASS_REF =
            Pattern.compile("io/campuscore/restfulapi/[A-Za-z0-9_/]+");
    private static final byte[] REST_CONTROLLER =
            "Lorg/springframework/web/bind/annotation/RestController;".getBytes();
    private static final byte[] CONTROLLER =
            "Lorg/springframework/stereotype/Controller;".getBytes();
    private static final byte[] CONTROLLER_ADVICE =
            "Lorg/springframework/web/bind/annotation/ControllerAdvice;".getBytes();
    private static final byte[] REST_CONTROLLER_ADVICE =
            "Lorg/springframework/web/bind/annotation/RestControllerAdvice;".getBytes();
    private static final byte[] OBJECT_MAPPER =
            "Lcom/fasterxml/jackson/databind/ObjectMapper;".getBytes();
    private static final byte[] PRINT_WRITER =
            "Ljava/io/PrintWriter;".getBytes();
    private static final byte[] RUNTIME_EXCEPTION =
            "java/lang/RuntimeException".getBytes();
    private static final String CAMPUSCORE_EXCEPTION_REF =
            "io/campuscore/restfulapi/exception/";

    /** Pre-existing controller classes that call a repository directly. */
    private static final Map<String, List<String>> CONTROLLER_TO_REPOSITORY_EXCEPTIONS = Map.of(
            "audit/controller/AdminAuditReadController", List.of("reads the page-size bound on the read repository"),
            "engagement/controller/ArticleTaxonomyController", List.of("legacy read-model access"),
            "mail/controller/MailController", List.of("legacy read-model access"),
            "thesis/assistant/controller/ThesisAssistantController", List.of("conversation read-model access"),
            "thesis/assistant/controller/ThesisAssistantInternalController", List.of("conversation read-model access"));

    /** Pre-existing repository classes that reference service-layer helpers. */
    private static final Map<String, List<String>> REPOSITORY_TO_SERVICE_EXCEPTIONS = Map.of(
            "thesis/assistant/repository/ThesisAssistantTurnRepository",
            List.of("reuses the assistant recovery-activity and timezone helpers"));

    private static final byte[] SPRING_JDBC_CORE =
            "org/springframework/jdbc/core".getBytes();
    private static final byte[] JAKARTA_PERSISTENCE =
            "jakarta/persistence".getBytes();
    private static final byte[] SPRING_DATA_JPA =
            "org/springframework/data/jpa".getBytes();
    private static final byte[] SPRING_ORM_JPA =
            "org/springframework/orm/jpa".getBytes();

    /**
     * Classes that still run JDBC statements outside repository/rls/config
     * packages. Shrink-only: an entry must be deleted when its class stops
     * referencing JDBC, and no entry may ever be added.
     */
    private static final Set<String> JDBC_OUTSIDE_DATA_LAYER_EXCEPTIONS = Set.of(
            "academic/job/EnrolledCountReconcileJob",
            "academic/registration/service/CreditLimitApplicationService",
            "academic/registration/service/RegistrationService",
            "academic/service/AcademicConductService",
            "academic/service/AcademicMutationService",
            "academic/service/AdminCatalogMutationService",
            "academic/service/LecturerMutationService",
            "audit/service/AdminAuditRecorder",
            "auth/service/AdminUserMutationService",
            "auth/service/DemoAccountGate",
            "system/controller/HealthController",
            "system/job/DatabaseKeepAliveScheduler",
            "thesis/assistant/job/PrerequisiteMapSyncJob",
            "thesis/assistant/service/AssistantFeedbackAdminService",
            "thesis/assistant/service/AssistantPersonalContextAdvisor",
            "thesis/assistant/service/SqlKnowledgeReleasePromoter",
            "thesis/assistant/service/SupabaseKnowledgeSyncService",
            "thesis/scope/ThesisDeanScope",
            "thesis/scope/ThesisDepartmentScope",
            "thesis/service/ThesisCouncilService",
            "thesis/service/ThesisLecturerWorkloadService",
            "thesis/service/ThesisMutationService",
            "thesis/service/ThesisNotificationService",
            "thesis/service/ThesisProgressService",
            "thesis/service/ThesisReportService",
            "thesis/service/ThesisReviewOutcome",
            "thesis/service/ThesisSupervisorService",
            "thesis/service/ThesisTopicService");

    /**
     * The JPA islands: every class that may reference jakarta.persistence or
     * the Spring Data/ORM JPA machinery. The set is frozen; a new island
     * member requires an explicit, reviewable entry here.
     */
    private static final Set<String> JPA_ISLAND_CLASSES = Set.of(
            "engagement/entity/ArticleAttachment",
            "engagement/entity/ArticleCategory",
            "engagement/entity/ArticleMediaGallery",
            "engagement/entity/ArticleTag",
            "engagement/repository/ArticleAttachmentRepository",
            "engagement/repository/ArticleCategoryRepository",
            "engagement/repository/ArticleMediaGalleryRepository",
            "engagement/repository/ArticleTagRepository",
            "thesis/assistant/config/AssistantDatabaseConfiguration",
            "thesis/entity/ThesisRegistrationRound",
            "thesis/entity/ThesisTopic",
            "thesis/repository/ThesisTopicRepository");

    @Test
    void controllersLiveInControllerPackagesAndAreAnnotated() throws Exception {
        List<String> problems = scan().stream()
                .filter(c -> c.simpleName().endsWith("Controller") || isController(c))
                .flatMap(c -> {
                    var problemsHere = new java.util.ArrayList<String>();
                    if (!inLayer(c, "controller")) {
                        problemsHere.add(c.binaryName() + " is not in a controller package");
                    }
                    if (c.simpleName().endsWith("Controller") && !isController(c)) {
                        problemsHere.add(c.binaryName() + " lacks @RestController/@Controller");
                    }
                    return problemsHere.stream();
                })
                .toList();
        assertTrue(problems.isEmpty(), () -> "controller placement violations: " + problems);
    }

    @Test
    void controllersDoNotDependOnRepositories() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> inLayer(c, "controller"))
                .filter(c -> !CONTROLLER_TO_REPOSITORY_EXCEPTIONS.containsKey(c.binaryName()))
                .filter(c -> references(c, "/repository/"))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(), () -> "new controller->repository dependencies: " + violations);
    }

    @Test
    void repositoriesDoNotDependOnControllersOrServices() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> inLayer(c, "repository"))
                .filter(c -> !REPOSITORY_TO_SERVICE_EXCEPTIONS.containsKey(c.binaryName()))
                .filter(c -> references(c, "/controller/") || references(c, "/service/"))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(), () -> "new repository->controller/service dependencies: " + violations);
    }

    @Test
    void jdbcCoreStaysInsideDataAccessPackages() throws Exception {
        List<String> violators = scan().stream()
                .filter(c -> contains(c.bytes(), SPRING_JDBC_CORE))
                .filter(c -> !inLayer(c, "repository") && !inLayer(c, "rls")
                        && !inLayer(c, "config"))
                .map(ScannedClass::binaryName)
                .toList();
        List<String> newViolators = violators.stream()
                .filter(v -> !JDBC_OUTSIDE_DATA_LAYER_EXCEPTIONS.contains(v))
                .sorted()
                .toList();
        List<String> staleExemptions = JDBC_OUTSIDE_DATA_LAYER_EXCEPTIONS.stream()
                .filter(v -> !violators.contains(v))
                .sorted()
                .toList();
        assertTrue(newViolators.isEmpty() && staleExemptions.isEmpty(),
                () -> "JDBC usage outside repository/rls/config: " + newViolators
                        + "; exemptions to remove (class no longer touches JDBC): "
                        + staleExemptions);
    }

    @Test
    void jpaStaysInsideExistingIslands() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> contains(c.bytes(), JAKARTA_PERSISTENCE)
                        || contains(c.bytes(), SPRING_DATA_JPA)
                        || contains(c.bytes(), SPRING_ORM_JPA))
                .filter(c -> !JPA_ISLAND_CLASSES.contains(c.binaryName()))
                .map(ScannedClass::binaryName)
                .sorted()
                .toList();
        assertTrue(violations.isEmpty(),
                () -> "JPA usage outside the frozen islands: " + violations);
    }

    @Test
    void dtoHoldersLiveInDtoPackages() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> c.simpleName().endsWith("Dtos"))
                .filter(c -> !inLayer(c, "dto"))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(), () -> "DTO holders outside dto packages: " + violations);
    }

    @Test
    void exceptionTypesLiveInExceptionPackagesAndExtendRuntimeFamily() throws Exception {
        List<String> problems = scan().stream()
                .filter(c -> c.simpleName().endsWith("Exception"))
                .flatMap(c -> {
                    var problemsHere = new java.util.ArrayList<String>();
                    if (!inLayer(c, "exception")) {
                        problemsHere.add(c.binaryName() + " is not in an exception package");
                    }
                    if (!referencesRuntimeExceptionFamily(c)) {
                        problemsHere.add(c.binaryName() + " does not extend RuntimeException or the app exception family");
                    }
                    return problemsHere.stream();
                })
                .toList();
        assertTrue(problems.isEmpty(), () -> "exception type violations: " + problems);
    }

    @Test
    void exceptionHandlersLiveInExceptionHandlerPackages() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> c.simpleName().endsWith("ExceptionHandler") || isControllerAdvice(c))
                .filter(c -> !inExceptionHandlerPackage(c))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(), () -> "exception handlers outside exception.handler packages: " + violations);
    }

    @Test
    void businessLayersDoNotDependOnExceptionHandlers() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> !inLayer(c, "exception"))
                .filter(c -> references(c, "/exception/handler/"))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(), () -> "non-exception packages depending on exception handlers: " + violations);
    }

    @Test
    void filtersWriteErrorBodiesOnlyThroughTheErrorWriter() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> c.simpleName().endsWith("Filter"))
                .filter(c -> contains(c.bytes(), OBJECT_MAPPER) || contains(c.bytes(), PRINT_WRITER))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(),
                () -> "filters constructing error bodies outside the error writer: " + violations);
    }

    /** Public/protected nested types in layered classes that may legitimately stay (reason required). */
    private static final Map<String, List<String>> SIGNATURE_NESTED_TYPE_EXCEPTIONS = Map.of();

    /** dto classes that legitimately referenced a domain type before model/ packages existed. */
    private static final Map<String, List<String>> DTO_TO_DOMAIN_EXCEPTIONS = Map.of(
            "thesis/dto/ThesisDeanOverviewDtos", List.of("uses thesis.model status enums"),
            "thesis/dto/ThesisGroupReadDtos", List.of("uses thesis.model status enums"),
            "thesis/dto/ThesisMutationDtos", List.of("uses thesis.model status enums"),
            "thesis/dto/ThesisRoundDtos", List.of("uses thesis.model round vocabulary"),
            "thesis/dto/ThesisTopicDtos", List.of("uses thesis.model topic vocabulary"),
            "engagement/dto/ArticleTaxonomyDtos", List.of("uses engagement.entity article entities"));

    @Test
    void layeredClassesDoNotDeclarePublicNestedDataTypesInSignatures() throws Exception {
        List<String> violations = new java.util.ArrayList<>();
        for (ScannedClass c : scan()) {
            if (!inLayer(c, "repository") && !inLayer(c, "service")
                    && !inLayer(c, "controller") && !inLayer(c, "job") && !inLayer(c, "client")) {
                continue;
            }
            Class<?> owner;
            try {
                owner = Class.forName(ROOT.replace('/', '.') + "." + c.binaryName().replace('/', '.'),
                        false, ArchitectureGuardTest.class.getClassLoader());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                violations.add(c.binaryName() + " could not be loaded for nested-type inspection");
                continue;
            }
            for (Class<?> nested : owner.getDeclaredClasses()) {
                int mod = nested.getModifiers();
                if (!java.lang.reflect.Modifier.isPublic(mod) && !java.lang.reflect.Modifier.isProtected(mod)) {
                    continue;
                }
                if (!appearsInSignature(owner, nested)) {
                    continue;
                }
                String key = c.binaryName() + "$" + nested.getSimpleName();
                if (!SIGNATURE_NESTED_TYPE_EXCEPTIONS.containsKey(key)) {
                    violations.add(key + " (declared inside " + c.binaryName() + ")");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "public/protected nested types in layered classes: " + violations);
    }

    /**
     * Recognized top-level packages: business modules and cross-cutting
     * packages. Anything else directly under the application root is a
     * misplaced class.
     */
    private static final Set<String> TOP_LEVEL_PACKAGES = Set.of(
            "academic", "audit", "auth", "common", "config", "engagement",
            "exception", "mail", "notification", "people", "security", "site",
            "system", "thesis");

    /**
     * Package roots that must stay empty: every class under them sits one
     * level deeper in a role subpackage (controller/service/repository/dto/
     * model/...). {@code exception} is absent on purpose: the shared base
     * exception family lives at the exception root by design.
     */
    private static final Set<String> PACKAGE_ROOTS_REQUIRING_ROLE_SUBPACKAGES = Set.of(
            "academic", "audit", "auth", "common", "config", "engagement",
            "mail", "notification", "people", "security", "site", "system",
            "thesis", "academic/registration", "security/ratelimit",
            "thesis/assistant");

    private static final Set<String> BUSINESS_MODULES = Set.of(
            "academic", "audit", "auth", "engagement", "mail", "notification",
            "people", "site", "thesis");

    private static final Set<String> CROSS_CUTTING_PACKAGES = Set.of(
            "common", "config", "exception", "security", "system");

    /**
     * Cross-cutting classes that legitimately referenced a business module
     * before this rule existed (class -> allowed business module -> reason).
     */
    private static final Map<String, Map<String, String>> CROSS_CUTTING_TO_BUSINESS_EXCEPTIONS = Map.of(
            "security/filter/AccountStateFilter", Map.of("auth",
                    "reads the account authorization row and model on every authenticated request"),
            "security/token/AuthTokenService", Map.of("auth",
                    "issues the auth module's token carriers"),
            "system/controller/HealthController", Map.of("thesis",
                    "readiness reports the assistant RLS verdict"));

    /**
     * Internal dependency direction inside the security package: each key is a
     * subpackage that must not reference the listed sibling subpackages. The
     * config package owns the wiring, so it has no entry (it may reach
     * everything).
     */
    private static final Map<String, List<String>> SECURITY_FORBIDDEN_REFS = Map.of(
            "security/model", List.of("security/filter", "security/token", "security/config",
                    "security/availability", "security/ratelimit"),
            "security/availability", List.of("security/filter", "security/token", "security/config",
                    "security/model", "security/ratelimit"),
            "security/filter", List.of("security/config", "security/ratelimit"),
            "security/token", List.of("security/config", "security/filter", "security/ratelimit"),
            "security/ratelimit/model", List.of("security/filter", "security/token", "security/config",
                    "security/availability", "security/ratelimit/filter", "security/ratelimit/service",
                    "security/ratelimit/properties", "security/ratelimit/annotation", "security/model"),
            "security/ratelimit/properties", List.of("security/filter", "security/token", "security/config",
                    "security/availability", "security/ratelimit/filter", "security/ratelimit/service",
                    "security/ratelimit/model", "security/ratelimit/annotation", "security/model"),
            "security/ratelimit/annotation", List.of("security/filter", "security/token", "security/config",
                    "security/availability", "security/ratelimit/filter", "security/ratelimit/service",
                    "security/ratelimit/properties", "security/model"),
            "security/ratelimit/service", List.of("security/filter", "security/token", "security/config",
                    "security/availability", "security/ratelimit/filter", "security/ratelimit/annotation",
                    "security/model"),
            "security/ratelimit/filter", List.of("security/filter", "security/token", "security/config",
                    "security/availability", "security/ratelimit/annotation", "security/model"));

    @Test
    void classesSitInRoleSubpackagesBelowPackageRoots() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> !c.binaryName().equals("RestfulApiApplication"))
                .flatMap(c -> {
                    var problemsHere = new java.util.ArrayList<String>();
                    String pkg = c.packagePath();
                    if (pkg.isEmpty()) {
                        problemsHere.add(c.binaryName() + " sits in the application root package");
                        return problemsHere.stream();
                    }
                    String topSegment = pkg.contains("/") ? pkg.substring(0, pkg.indexOf('/')) : pkg;
                    if (!TOP_LEVEL_PACKAGES.contains(topSegment)) {
                        problemsHere.add(c.binaryName() + " is not inside a recognized top-level package");
                    } else if (PACKAGE_ROOTS_REQUIRING_ROLE_SUBPACKAGES.contains(pkg)) {
                        problemsHere.add(c.binaryName() + " sits directly in package root " + pkg
                                + " instead of a role subpackage");
                    }
                    return problemsHere.stream();
                })
                .toList();
        assertTrue(violations.isEmpty(), () -> "classes outside role subpackages: " + violations);
    }

    @Test
    void crossCuttingPackagesDoNotDependOnBusinessModules() throws Exception {
        List<String> violations = new java.util.ArrayList<>();
        for (ScannedClass c : scan()) {
            String pkg = c.packagePath();
            String topSegment = pkg.contains("/") ? pkg.substring(0, pkg.indexOf('/')) : pkg;
            if (!CROSS_CUTTING_PACKAGES.contains(topSegment)) {
                continue;
            }
            for (String module : referencedTopSegments(c)) {
                if (!BUSINESS_MODULES.contains(module)) {
                    continue;
                }
                Map<String, String> tolerated = CROSS_CUTTING_TO_BUSINESS_EXCEPTIONS.get(c.binaryName());
                if (tolerated == null || !tolerated.containsKey(module)) {
                    violations.add(c.binaryName() + " -> " + module);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "cross-cutting packages depending on business modules: " + violations);
    }

    @Test
    void securityPackagesKeepTheirDependencyDirection() throws Exception {
        List<String> violations = new java.util.ArrayList<>();
        for (ScannedClass c : scan()) {
            String pkg = c.packagePath();
            for (Map.Entry<String, List<String>> rule : SECURITY_FORBIDDEN_REFS.entrySet()) {
                if (!pkg.equals(rule.getKey()) && !pkg.startsWith(rule.getKey() + "/")) {
                    continue;
                }
                for (String forbidden : rule.getValue()) {
                    if (references(c, "/" + forbidden + "/")) {
                        violations.add(c.binaryName() + " -> " + forbidden);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "security packages with a backwards dependency: " + violations);
    }

    private static java.util.Set<String> referencedTopSegments(ScannedClass c) {
        var modules = new java.util.LinkedHashSet<String>();
        var matcher = CLASS_REF.matcher(new String(c.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1));
        while (matcher.find()) {
            String rel = matcher.group().substring(ROOT.length() + 1);
            int slash = rel.indexOf('/');
            if (slash > 0 && !rel.equals(c.binaryName())) {
                modules.add(rel.substring(0, slash));
            }
        }
        return modules;
    }

    @Test
    void modelAndDomainPackagesDoNotDependOnLayerPackages() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> inLayer(c, "model") || inLayer(c, "domain") || inLayer(c, "entity"))
                .filter(c -> references(c, "/repository/") || references(c, "/service/")
                        || references(c, "/controller/"))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(),
                () -> "model/domain/entity classes depending on layered packages: " + violations);
    }

    @Test
    void dtoPackagesDoNotDependOnModelPackages() throws Exception {
        List<String> violations = scan().stream()
                .filter(c -> inLayer(c, "dto"))
                .filter(c -> !DTO_TO_DOMAIN_EXCEPTIONS.containsKey(c.binaryName()))
                .filter(c -> references(c, "/model/") || references(c, "/domain/") || references(c, "/entity/"))
                .map(ScannedClass::binaryName)
                .toList();
        assertTrue(violations.isEmpty(),
                () -> "dto classes depending on model/domain packages: " + violations);
    }

    private static boolean appearsInSignature(Class<?> owner, Class<?> nested) {
        for (java.lang.reflect.Method m : owner.getDeclaredMethods()) {
            if (mentions(m.getGenericReturnType(), nested)) {
                return true;
            }
            for (java.lang.reflect.Type p : m.getGenericParameterTypes()) {
                if (mentions(p, nested)) {
                    return true;
                }
            }
        }
        for (java.lang.reflect.Constructor<?> c : owner.getDeclaredConstructors()) {
            for (java.lang.reflect.Type p : c.getGenericParameterTypes()) {
                if (mentions(p, nested)) {
                    return true;
                }
            }
        }
        for (java.lang.reflect.Field f : owner.getDeclaredFields()) {
            if (mentions(f.getGenericType(), nested)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentions(java.lang.reflect.Type type, Class<?> target) {
        if (type == null) {
            return false;
        }
        if (type.equals(target)) {
            return true;
        }
        if (type instanceof java.lang.reflect.ParameterizedType pt) {
            if (mentions(pt.getRawType(), target)) {
                return true;
            }
            for (java.lang.reflect.Type arg : pt.getActualTypeArguments()) {
                if (mentions(arg, target)) {
                    return true;
                }
            }
        }
        if (type instanceof java.lang.reflect.GenericArrayType at) {
            return mentions(at.getGenericComponentType(), target);
        }
        if (type instanceof java.lang.reflect.WildcardType wt) {
            for (java.lang.reflect.Type b : wt.getUpperBounds()) {
                if (mentions(b, target)) {
                    return true;
                }
            }
            for (java.lang.reflect.Type b : wt.getLowerBounds()) {
                if (mentions(b, target)) {
                    return true;
                }
            }
        }
        if (type instanceof Class<?> cls && cls.isArray()) {
            return mentions(cls.getComponentType(), target);
        }
        return false;
    }

    private static boolean inExceptionHandlerPackage(ScannedClass c) {
        String pkg = c.packagePath();
        return pkg.endsWith("exception/handler") || pkg.contains("exception/handler/");
    }

    private static boolean isControllerAdvice(ScannedClass c) {
        return contains(c.bytes(), CONTROLLER_ADVICE) || contains(c.bytes(), REST_CONTROLLER_ADVICE);
    }

    private static boolean referencesRuntimeExceptionFamily(ScannedClass c) {
        return contains(c.bytes(), RUNTIME_EXCEPTION)
                || new String(c.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains(CAMPUSCORE_EXCEPTION_REF);
    }

    private static boolean inLayer(ScannedClass c, String layer) {
        String pkg = c.packagePath();
        return pkg.equals(layer) || pkg.endsWith("/" + layer) || pkg.contains("/" + layer + "/");
    }

    private static boolean isController(ScannedClass c) {
        return contains(c.bytes(), REST_CONTROLLER) || contains(c.bytes(), CONTROLLER);
    }

    private static boolean references(ScannedClass c, String layerSegment) {
        var matcher = CLASS_REF.matcher(new String(c.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1));
        while (matcher.find()) {
            String ref = matcher.group();
            if (ref.contains(layerSegment) && !ref.equals(ROOT + "/" + c.binaryName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static List<ScannedClass> scan() throws IOException {
        Path classesDir = classesDirectory();
        try (Stream<Path> paths = Files.walk(classesDir.resolve(ROOT))) {
            return paths.filter(p -> p.toString().endsWith(".class"))
                    .map(classesDir::relativize)
                    .map(p -> p.toString().replace('\\', '/').replace(".class", ""))
                    .filter(n -> !n.contains("$"))
                    .filter(n -> n.startsWith(ROOT + "/"))
                    .map(n -> {
                        String rel = n.substring(ROOT.length() + 1);
                        try {
                            return new ScannedClass(rel, Files.readAllBytes(classesDir.resolve(n + ".class")));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
    }

    private static Path classesDirectory() {
        try {
            Path testClasses = Path.of(ArchitectureGuardTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path classes = testClasses.resolveSibling("classes");
            if (Files.isDirectory(classes)) {
                return classes;
            }
            return testClasses;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private record ScannedClass(String binaryName, byte[] bytes) {
        String packagePath() {
            int idx = binaryName.lastIndexOf('/');
            return idx < 0 ? "" : binaryName.substring(0, idx);
        }

        String simpleName() {
            int idx = binaryName.lastIndexOf('/');
            return idx < 0 ? binaryName : binaryName.substring(idx + 1);
        }
    }
}
