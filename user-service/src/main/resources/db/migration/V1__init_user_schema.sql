-- =====================================================================================
-- FILE: V1__init_user_schema.sql
-- MODULE: user-service (Database Migration via Flyway)
-- PURPOSE: Creates core relational tables for users, roles, and refresh tokens.
--
-- FLYWAY CONVENTION:
-- - 'V1__' means Version 1. Two underscores '__' are MANDATORY in Flyway syntax.
-- - Flyway executes scripts once in order, recording checksums in 'flyway_schema_history'.
--
-- TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
-- Q1: Why do we index 'email' and 'token_jti'?
-- A1: 'email' is the lookup key on every single login attempt. An index converts an O(N) 
--     full table scan into an O(log N) B-Tree search. Same for 'token_jti' during token refresh.
--
-- Q2: Why use TIMESTAMP WITH TIME ZONE (TIMESTAMPTZ) instead of TIMESTAMP?
-- A2: Plain TIMESTAMP stores clock time without timezone offset. If servers run across UTC 
--     and local zones, plain timestamps lead to subtle DST and token expiration calculation bugs.
-- =====================================================================================

-- 1. Create Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    mobile_no VARCHAR(20),
    profile_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    modified_by BIGINT
);

-- 3. Create User Roles Join Table (ManyToMany)
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

-- 4. Create Refresh Tokens Table (Token Whitelist / Blacklist tracking)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_jti VARCHAR(100) NOT NULL UNIQUE,
    user_email VARCHAR(150) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Indexes for fast query performance
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_jti ON refresh_tokens(token_jti);
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);

-- 6. Initial Role Seeds
INSERT INTO roles (name, description, is_active) VALUES 
('ROLE_USER', 'Standard Customer Account', TRUE),
('ROLE_ADMIN', 'Platform Administrator', TRUE),
('ROLE_SELLER', 'Merchant / Seller Account', TRUE)
ON CONFLICT (name) DO NOTHING;
