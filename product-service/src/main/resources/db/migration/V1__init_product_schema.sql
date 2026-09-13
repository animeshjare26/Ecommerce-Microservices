-- =====================================================================================
-- FILE: product-service/src/main/resources/db/migration/V1__init_product_schema.sql
-- MODULE: product-service (Product Catalog & Category Microservice)
-- PURPOSE: Baseline Flyway migration creating categories and products tables with
--          composite indexes, foreign key constraints, and default seed data.
--
-- DESIGN PATTERN / ARCHITECTURAL CONCEPT:
-- - Immutable Database Migration Pattern (Flyway versioned V1__).
-- - Composite B-Tree Indexing: Optimizes high-throughput filtering by category & active status.
-- - Foreign Key Referential Integrity: Enforces category relationships with ON DELETE RESTRICT.
--
-- READING ORDER:
-- - Read PREVIOUS: src/main/resources/application.yml
-- - Read THIS FILE: Understand tables, indexes, and constraints.
-- - Read NEXT: src/main/java/com/ecommerce/product/entity/Category.java
--
-- TRICKY INTERVIEW QUESTIONS & ARCHITECTURAL WISDOM:
-- Q1: Why do we create a composite index on (category_id, active) instead of two single indexes?
-- A1: When users browse a category, 99.9% of queries look like:
--     `SELECT * FROM products WHERE category_id = ? AND active = true`.
--     A composite index `(category_id, active)` allows PostgreSQL to locate matching rows
--     in a single B-tree index traverse, avoiding an expensive BitmapAnd index merge.
--
-- Q2: Why use NUMERIC(12, 2) for price instead of FLOAT or DOUBLE?
-- A2: FLOAT and DOUBLE use IEEE 754 binary floating-point representation, which cannot
--     accurately represent decimal fractions (e.g., 0.1 + 0.2 != 0.3). NUMERIC/DECIMAL
--     stores exact base-10 digits, which is mandatory for financial and e-commerce accuracy.
-- =====================================================================================

-- 1. Create Categories Table
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

-- Index for category slug lookups
CREATE INDEX IF NOT EXISTS idx_categories_slug ON categories(slug);
CREATE INDEX IF NOT EXISTS idx_categories_is_active ON categories(is_active);

-- 2. Create Products Table
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    stock_quantity BIGINT NOT NULL DEFAULT 0,
    low_stock_threshold BIGINT NOT NULL DEFAULT 10,
    stock_status VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK',
    seller_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    category_id BIGINT REFERENCES categories(id) ON DELETE RESTRICT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

-- B-Tree Performance Indexes
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_category_is_active ON products(category_id, is_active);
CREATE INDEX IF NOT EXISTS idx_products_seller ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_price ON products(price);
CREATE INDEX IF NOT EXISTS idx_products_stock_status ON products(stock_status);
CREATE INDEX IF NOT EXISTS idx_products_created_at ON products(created_at DESC);

-- 3. Seed Default Categories for immediate out-of-the-box functionality
INSERT INTO categories (name, slug, description, is_active) VALUES
('Electronics', 'electronics', 'Smartphones, laptops, monitors, and gadgets', true),
('Fashion', 'fashion', 'Apparel, shoes, accessories, and wear', true),
('Home & Kitchen', 'home-kitchen', 'Cookware, furniture, decor, and appliances', true),
('Books & Media', 'books-media', 'Paperbacks, eBooks, audiobooks, and media', true)
ON CONFLICT (slug) DO NOTHING;

-- 4. Seed Demo Products
INSERT INTO products (sku, name, description, price, stock_quantity, low_stock_threshold, stock_status, seller_id, is_active, category_id) VALUES
('ELEC-IPHONE15-128', 'iPhone 15 Pro 128GB - Natural Titanium', 'Apple A17 Pro Bionic chip, titanium chassis, 48MP camera', 999.00, 150, 20, 'IN_STOCK', 1, true, 1),
('ELEC-MACBOOK-M3', 'MacBook Air 15-inch M3 - Midnight', 'Liquid Retina Display, 16GB RAM, 512GB SSD', 1499.00, 45, 10, 'IN_STOCK', 1, true, 1),
('ELEC-SONY-WH1000XM5', 'Sony WH-1000XM5 Noise-Canceling Headphones', 'Industry-leading noise cancellation, 30-hour battery life', 398.00, 5, 15, 'LOW_STOCK', 1, true, 1),
('FASH-NIKE-AIRMAX', 'Nike Air Max 270 - Triple Black (Size 10)', 'Breathable mesh upper with large volume Max Air heel unit', 160.00, 0, 10, 'OUT_OF_STOCK', 2, true, 2),
('BOOK-DDIA-KLEPPMANN', 'Designing Data-Intensive Applications', 'The definitive guide to distributed systems and storage architectures by Martin Kleppmann', 45.00, 80, 15, 'IN_STOCK', 2, true, 4)
ON CONFLICT (sku) DO NOTHING;
