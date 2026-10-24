package io.campuscore.restfulapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
