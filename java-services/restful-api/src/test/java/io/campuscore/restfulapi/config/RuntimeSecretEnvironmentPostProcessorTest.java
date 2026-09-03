package io.campuscore.restfulapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class RuntimeSecretEnvironmentPostProcessorTest {
    @TempDir Path temporary;

    @Test
    void fileValueTakesPrecedenceOverDirectConfiguration() throws Exception {
        Path secret = temporary.resolve("deepseek");
        Files.writeString(secret, "runtime-value\n");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("deepseek.enabled", "true")
                .withProperty("DEEPSEEK_API_KEY_FILE", secret.toString())
                .withProperty("deepseek.api-key", "direct-value");

        new RuntimeSecretEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("runtime-value", environment.getProperty("deepseek.api-key"));
    }

    @Test
    void configuredMissingFileFailsClosedWithoutLeakingPathContents() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET_FILE", temporary.resolve("missing").toString());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new RuntimeSecretEnvironmentPostProcessor().postProcessEnvironment(environment, null));

        assertEquals("JWT_SECRET_FILE secret file is missing or is not a regular file", failure.getMessage());
    }

    @Test
    void disabledProviderDoesNotRequireAnOptionalKeyFile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("deepseek.enabled", "false")
                .withProperty("DEEPSEEK_API_KEY_FILE", temporary.resolve("not-provisioned").toString());

        new RuntimeSecretEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        // No provider value is injected while the integration is disabled.
        org.junit.jupiter.api.Assertions.assertNull(environment.getProperty("deepseek.api-key"));
    }

    @Test
    void enabledProviderWithoutFileFailsClosed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("deepseek.enabled", "true");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new RuntimeSecretEnvironmentPostProcessor().postProcessEnvironment(environment, null));

        assertEquals("DEEPSEEK_API_KEY_FILE is required when its integration is enabled", failure.getMessage());
    }

    @Test
    void enabledProviderWithDirectKeyDoesNotRequireFile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("deepseek.enabled", "true")
                .withProperty("deepseek.api-key", "direct-configured-key");

        new RuntimeSecretEnvironmentPostProcessor().postProcessEnvironment(environment, null);

        assertEquals("direct-configured-key", environment.getProperty("deepseek.api-key"));
    }
}
