package com.deployforge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI description served at {@code /api/docs} with a Swagger UI at {@code /api/docs/ui}. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI deployForgeOpenApi(DeployForgeProperties properties) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("DeployLane API")
                                .version("v1")
                                .description(
                                        """
                                        Deploy. Monitor. Debug. Fix.

                                        Authenticate by sending `Authorization: Bearer <access token>`.
                                        Obtain an access token through the GitHub OAuth flow
                                        (`GET /api/v1/auth/github/authorize`) or by refreshing an existing
                                        session (`POST /api/v1/auth/refresh`).

                                        Secret values are never returned by this API. Environment variable
                                        endpoints expose metadata only.""")
                                .license(new License().name("MIT")))
                .servers(List.of(new Server().url(properties.publicBackendUrl())))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
