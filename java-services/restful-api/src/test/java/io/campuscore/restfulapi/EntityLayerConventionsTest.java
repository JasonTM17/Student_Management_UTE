package io.campuscore.restfulapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.campuscore.restfulapi.academic.entity.Enrollment;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Conventions for the JPA entity layer: every {@code @Entity} class lives in
 * an {@code entity} package below the owning feature module and declares a
 * primary key — either {@code @Id} / {@code @EmbeddedId} fields or an
 * {@code @IdClass} reference.
 */
class EntityLayerConventionsTest {

    private static final String ROOT = "io/campuscore/restfulapi";
    private static final byte[] ENTITY_ANNOTATION =
            "Ljakarta/persistence/Entity;".getBytes();

    @Test
    void everyEntityLivesInAnEntityPackageAndDeclaresAnId() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String binaryName : entityClassNames()) {
            String pkg = binaryName.contains("/")
                    ? binaryName.substring(0, binaryName.lastIndexOf('/'))
                    : "";
            List<String> segments = List.of(pkg.split("/"));
            if (!segments.contains("entity")) {
                problems.add(binaryName + " is an @Entity outside an entity package");
                continue;
            }
            Class<?> type;
            try {
                type = Class.forName(binaryName.replace('/', '.'), false,
                        EntityLayerConventionsTest.class.getClassLoader());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                problems.add(binaryName + " could not be loaded");
                continue;
            }
            if (!hasPrimaryKey(type)) {
                problems.add(binaryName + " has no @Id/@EmbeddedId field and no @IdClass");
            }
        }
        assertTrue(problems.isEmpty(), () -> "entity convention violations: " + problems);
    }

    @Test
    void entityClassesExist() throws Exception {
        assertTrue(!entityClassNames().isEmpty(), "no @Entity classes were discovered");
    }

    @Test
    void enrollmentMapsCurriculumRegistrationSourceColumn() throws Exception {
        Field field = Enrollment.class.getDeclaredField("registrationSource");
        Column column = field.getAnnotation(Column.class);

        assertEquals(String.class, field.getType());
        assertEquals("\"registrationSource\"", column.name());
        assertEquals(40, column.length());
    }

    private static boolean hasPrimaryKey(Class<?> type) {
        if (type.isAnnotationPresent(IdClass.class)) {
            return true;
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> entityClassNames() throws IOException {
        Path classesDir = classesDirectory();
        try (Stream<Path> paths = Files.walk(classesDir.resolve(ROOT))) {
            return paths.filter(p -> p.toString().endsWith(".class"))
                    .map(classesDir::relativize)
                    .map(p -> p.toString().replace('\\', '/').replace(".class", ""))
                    .filter(n -> !n.contains("$"))
                    .filter(n -> n.startsWith(ROOT + "/"))
                    .filter(n -> containsMarker(classesDir, n))
                    .toList();
        }
    }

    private static boolean containsMarker(Path classesDir, String binaryName) {
        try {
            return contains(Files.readAllBytes(classesDir.resolve(binaryName + ".class")),
                    ENTITY_ANNOTATION);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    private static Path classesDirectory() {
        try {
            Path testClasses = Path.of(EntityLayerConventionsTest.class.getProtectionDomain()
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
}
