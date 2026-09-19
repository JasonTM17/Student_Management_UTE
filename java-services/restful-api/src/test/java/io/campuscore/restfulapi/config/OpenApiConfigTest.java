package io.campuscore.restfulapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    @DisplayName("OpenAPI Bean should define HCM-UTE metadata, security schemes, and production servers")
    void openApiBeanShouldBeProperlyConfigured() {
        OpenAPI openAPI = openApiConfig.campusCoreOpenAPI("4010", "campuscore-restful-api");

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).contains("HCM-UTE");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("2.4.0 (Enterprise Architecture)");
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("daotao@ute.edu.vn");

        // Assert security scheme
        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
        SecurityScheme securityScheme = openAPI.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(securityScheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(securityScheme.getScheme()).isEqualTo("bearer");
        assertThat(securityScheme.getBearerFormat()).isEqualTo("JWT");

        // Assert servers
        assertThat(openAPI.getServers()).hasSize(2);
        assertThat(openAPI.getServers().get(0).getUrl()).isEqualTo("http://127.0.0.1:4010");
        assertThat(openAPI.getServers().get(1).getUrl()).isEqualTo("https://campusute.io.vn");
    }
}
