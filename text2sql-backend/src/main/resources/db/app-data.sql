-- Application database SEED data: users, conversations and messages.
--
-- Purpose: a fresh database has no accounts and no data, which makes the role-based data scope
-- impossible to demonstrate. This script creates one admin and two regular users plus conversations
-- owned by different accounts, so the difference is directly observable:
--
--   admin  -> GET /api/conversations returns all 4 seeded conversations
--   alice  -> returns only her 2
--   bob    -> returns only his 1
--
-- Credentials (development/demo only — change before any real use):
--   admin / admin123   role=admin
--   alice / alice123   role=user
--   bob   / bob123     role=user
--
-- Executed by docker-compose init (MySQL) as 02-app-data.sql, i.e. only when the data directory is
-- empty. Ids and timestamps are fixed so the dataset is deterministic and the file can be re-run
-- against an existing database without creating duplicates.
--
-- Not wired into spring.sql.init, so the H2 test profile stays free of seed rows.
--
-- IMPORTANT: pin the connection charset before any data statement. The MySQL container's entrypoint
-- pipes this file into the `mysql` CLI, which under a POSIX/C locale defaults its connection charset
-- to latin1. Without this line the UTF-8 bytes below are reinterpreted as latin1 and stored
-- double-encoded ("管理" turns into "ç®¡ç†...").
SET NAMES utf8mb4;

-- --- Users -------------------------------------------------------------------------------------
INSERT IGNORE INTO app_user (id, username, password_hash, display_name, role, created_at) VALUES
    ('seed-user-admin', 'admin', '$2a$10$TrWylp9QGqLxpYr2Jwg5Juh5R6NY7C37bUesnAmgAucho0JuB.r/a', '系统管理员', 'admin', 1792454400000),
    ('seed-user-alice', 'alice', '$2a$10$lTFQqLn0iV.MnoAg505w1OHR9WGSB.vJYc/IcPM7swx/rZfIemVQW', '爱丽丝',     'user',  1792454401000),
    ('seed-user-bob',   'bob',   '$2a$10$VNvecnt3nRoQeX3L1H1Ov.xcetPnobfsji7fmh55A7w1n6R5LoA/C', '鲍勃',       'user',  1792454402000);

-- --- Conversations -----------------------------------------------------------------------------
-- Two belong to alice, one to bob, one to admin. Ordered below so that the most recently updated
-- conversation sorts first.
INSERT IGNORE INTO conversation (id, user_id, title, datasource_id, model_config_id, created_at, updated_at) VALUES
    ('seed-conv-alice-1', 'seed-user-alice', '各品类销售额对比',   'demo', NULL, 1792458000000, 1792461600000),
    ('seed-conv-alice-2', 'seed-user-alice', '复购率最高的客户',   'demo', NULL, 1792465200000, 1792465200000),
    ('seed-conv-bob-1',   'seed-user-bob',   '本月订单趋势',       'demo', NULL, 1792468800000, 1792472400000),
    ('seed-conv-admin-1', 'seed-user-admin', '管理员全局数据核查', 'demo', NULL, 1792476000000, 1792476000000);

-- --- Messages ----------------------------------------------------------------------------------
-- One complete exchange in alice's first conversation, so opening it renders an answer.
INSERT IGNORE INTO message (id, conversation_id, role, content, sql_text, chart_json, rows_json, created_at) VALUES
    ('seed-msg-alice-1-user', 'seed-conv-alice-1', 'user', '各品类的销售额分别是多少？', NULL, NULL, NULL, 1792458000000),
    ('seed-msg-alice-1-bot',  'seed-conv-alice-1', 'assistant', '按销售额降序排列各品类。',
     'SELECT p.category, SUM(oi.quantity * oi.unit_price) AS amount FROM order_items oi JOIN products p ON p.id = oi.product_id GROUP BY p.category ORDER BY amount DESC',
     '{"type":"BAR","xField":"category","yField":"amount","title":"各品类销售额"}',
     '[{"category":"Electronics","amount":12580.5}]', 1792461600000);
