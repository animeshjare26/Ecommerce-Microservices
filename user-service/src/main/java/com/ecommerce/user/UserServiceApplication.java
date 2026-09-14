package com.ecommerce.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * =====================================================================================
 * FILE: UserServiceApplication.java
 * MODULE: user-service
 * PURPOSE: Main Spring Boot bootstrap and entry point for the User & Identity microservice.
 * 
 * DESIGN PATTERN: Bootstrap / Application Runner Pattern.
 * 
 * READING ORDER:
 * - START APPLICATION HERE: This class starts embedded Tomcat on port 8081.
 * - Read NEXT: SecurityConfiguration.java, AuthController.java
 * 
 * TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
 * Q1: What are the three core meta-annotations that compose `@SpringBootApplication`?
 * A1: 1. `@SpringBootConfiguration`: Marks the class as a source of bean definitions (specialized @Configuration).
 *     2. `@EnableAutoConfiguration`: Tells Spring Boot to guess and configure beans based 
 *        on classpath jar dependencies (e.g., finding tomcat-embed-core starts Tomcat, 
 *        finding postgresql driver configures DataSourceAutoConfiguration).
 *     3. `@ComponentScan`: Tells Spring to scan for @Component, @Service, @Repository, 
 *        and @Controller in the current package and all sub-packages recursively.
 * 
 * Q2: What actually happens during `SpringApplication.run()`?
 * A2: 1. Creates ApplicationContext (AnnotationConfigServletWebServerApplicationContext).
 *     2. Sets up the Environment (loads application.yml, system properties, environment variables).
 *     3. Triggers ApplicationContextInitializer listeners.
 *     4. Runs BeanFactoryPostProcessors and loads BeanDefinitions.
 *     5. Starts embedded servlet container (Tomcat on port 8081).
 *     6. Invokes ApplicationRunner and CommandLineRunner beans.
 * =====================================================================================
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
        System.out.println("User Service Started !");
    }
}
