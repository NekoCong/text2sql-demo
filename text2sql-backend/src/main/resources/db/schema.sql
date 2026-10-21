-- Demo e-commerce schema for Text2SqlAgent.
-- Kept deliberately small and portable (H2 + MySQL compatible) so the agent's
-- text-to-SQL behaviour is easy to reason about. Live introspection
-- (SchemaIntrospector) is the single source of truth -- this file only seeds it.

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    id         BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    city       VARCHAR(100),
    created_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (email)
);

CREATE TABLE products (
    id       BIGINT        NOT NULL,
    name     VARCHAR(150)  NOT NULL,
    category VARCHAR(100),
    price    DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE orders (
    id           BIGINT        NOT NULL,
    customer_id  BIGINT        NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE TABLE order_items (
    id         BIGINT        NOT NULL,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INT           NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
