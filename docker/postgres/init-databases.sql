-- =====================================================================================
-- FILE: init-databases.sql
-- PURPOSE: Automatically creates isolated logical databases for all microservices
--          when the PostgreSQL container boots up for the first time.
-- DATABASE PER SERVICE PATTERN:
-- Every microservice has strict private ownership of its schema and database.
-- =====================================================================================

CREATE DATABASE user_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
