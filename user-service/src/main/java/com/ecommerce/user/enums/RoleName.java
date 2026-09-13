package com.ecommerce.user.enums;

/**
 * =====================================================================================
 * FILE: RoleName.java
 * MODULE: user-service
 * PURPOSE: Enumerates Role-Based Access Control (RBAC) security roles.
 * 
 * SPRING SECURITY CONVENTION:
 * Spring Security's `hasRole('USER')` expression automatically looks for authority
 * strings prefixed with "ROLE_". Hence, defining role names with the "ROLE_" prefix
 * ensures seamless compatibility with `@PreAuthorize("hasRole('ADMIN')")`.
 * =====================================================================================
 */
public enum RoleName {
    ROLE_USER,
    ROLE_ADMIN,
    ROLE_SELLER
}
