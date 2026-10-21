-- Demo seed data (small, deterministic) for the e-commerce schema.

INSERT INTO customers (id, name, email, city, created_at) VALUES
    (1, 'Alice Chen',   'alice@example.com',   'Shanghai',  '2024-01-05 09:12:00'),
    (2, 'Bob Li',       'bob@example.com',     'Beijing',   '2024-01-18 14:30:00'),
    (3, 'Carol Wang',   'carol@example.com',   'Shanghai',  '2024-02-02 20:05:00'),
    (4, 'David Zhang',  'david@example.com',   'Shenzhen',  '2024-02-20 11:45:00'),
    (5, 'Eve Liu',      'eve@example.com',     'Hangzhou',  '2024-03-01 08:00:00'),
    (6, 'Frank Zhao',   'frank@example.com',   'Beijing',   '2024-03-15 16:20:00');

INSERT INTO products (id, name, category, price) VALUES
    (1, 'Mechanical Keyboard', 'Electronics', 499.00),
    (2, 'Wireless Mouse',      'Electronics', 129.00),
    (3, '27-inch Monitor',     'Electronics', 1899.00),
    (4, 'Java in Depth',       'Books',       89.50),
    (5, 'Coffee Beans 1kg',    'Grocery',     168.00),
    (6, 'Standing Desk',       'Furniture',   1299.00);

INSERT INTO orders (id, customer_id, status, total_amount, created_at) VALUES
    (1001, 1, 'PAID',    628.00,  '2024-03-02 10:00:00'),
    (1002, 1, 'SHIPPED', 1899.00, '2024-03-20 15:30:00'),
    (1003, 2, 'PAID',    217.50,  '2024-03-21 09:10:00'),
    (1004, 3, 'PENDING', 168.00,  '2024-04-01 12:00:00'),
    (1005, 4, 'PAID',    1299.00, '2024-04-05 18:45:00'),
    (1006, 6, 'CANCELLED', 129.00, '2024-04-07 08:20:00'),
    (1007, 3, 'SHIPPED', 998.00,  '2024-04-10 13:15:00');

INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES
    (1, 1001, 1, 1, 499.00),
    (2, 1001, 2, 1, 129.00),
    (3, 1002, 3, 1, 1899.00),
    (4, 1003, 4, 1, 89.50),
    (5, 1003, 2, 1, 129.00),
    (6, 1004, 5, 1, 168.00),
    (7, 1005, 6, 1, 1299.00),
    (8, 1006, 2, 1, 129.00),
    (9, 1007, 1, 2, 499.00);
