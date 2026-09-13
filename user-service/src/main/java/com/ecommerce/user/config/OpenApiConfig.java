package com.ecommerce.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * =====================================================================================
 * FILE: OpenApiConfig.java
 * MODULE: user-service
 * PURPOSE: Configures Swagger 3 / OpenAPI metadata and Bearer JWT authentication in Swagger UI.
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: How does OpenAPI security scheme configuration help frontend developers and QA?
 * A1: It adds an "Authorize" button in the Swagger UI (/swagger-ui.html). Testers can paste 
 *     a Bearer JWT once, and Swagger automatically attaches `Authorization: Bearer <token>` 
 *     to all interactive test requests, eliminating manual Postman header configuration!
 * =====================================================================================
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("User & Identity Microservice API")
                        .version("1.0.0")
                        .description("REST API documentation for User Registration, Authentication, and Token Management")
                        .contact(new Contact().name("Ecommerce Architecture Team").email("architect@ecommerce.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token obtained from /api/auth/login")));
    }
}
