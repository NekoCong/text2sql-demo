-- 应用库种子数据：智慧校园演示账号、数据范围映射、数据源授权与示例会话。
--
-- 演示账号（开发/演示环境，任何真实使用前务必更换）：
--   admin       / admin123      角色 admin      全量可见，同时可维护数据源白名单
--   student01   / student123    角色 student    只能看到自己（张伟，1 班）
--   student02   / student123    角色 student    只能看到自己（李娜，1 班）
--   student03   / student123    角色 student    只能看到自己（王强，2 班）
--   counselor01 / counselor123  角色 counselor  可看到 1 班与 2 班
--
-- 数据范围的语义（见 openspec/changes/campus-data-permission/specs/data-permission/spec.md）：
--   学生   -> class_id + student_id + student_no 各一条，用于把查询收敛到本人
--   辅导员 -> 每条 class_id 一行，可覆盖多个班级
--   管理员 -> 不写任何 user_scope 行，即全量
--
-- 业务库 id（student_id）在种子中固定，保证演示数据可被逐条核对；真实系统若业务主键会变化，
-- 应改为按 student_no 在请求期解析（见 design.md 的风险项）。
--
-- 只在空数据目录执行（docker compose init）。要重新播种：docker compose down -v。
--
-- 重要：在任何数据语句之前先固定连接字符集，否则 MySQL 容器的 mysql 客户端可能按 latin1
-- 解释下面的 UTF-8 字节，中文会变成双重编码。
SET NAMES utf8mb4;

-- --- 账号 ---------------------------------------------------------------------------------------
INSERT IGNORE INTO app_user (id, username, password_hash, display_name, role, created_at) VALUES
    ('seed-user-admin',       'admin',       '$2a$10$CyF0X5/O9C8iH.B/CqTuveiWyGFofEgFWzn5Y0vW8XtkN1QEFDH5y', '系统管理员', 'admin',     1792454400000),
    ('seed-user-student01',   'student01',   '$2a$10$HhGCCvK2Wn4ts4DCM6kREO3gjbQax9MBY507RyNcuywmKv10Fvsfu', '张伟',       'student',   1792454401000),
    ('seed-user-student02',   'student02',   '$2a$10$HhGCCvK2Wn4ts4DCM6kREO3gjbQax9MBY507RyNcuywmKv10Fvsfu', '李娜',       'student',   1792454402000),
    ('seed-user-student03',   'student03',   '$2a$10$HhGCCvK2Wn4ts4DCM6kREO3gjbQax9MBY507RyNcuywmKv10Fvsfu', '王强',       'student',   1792454403000),
    ('seed-user-counselor01', 'counselor01', '$2a$10$6jPFACYIlLsps.n.aZSWoe.Ro40GaglrRSSHjt0/B.qYTedcsYs7e', '李辅导员',   'counselor', 1792454404000);

-- --- 数据范围映射 -------------------------------------------------------------------------------
INSERT IGNORE INTO user_scope (user_id, scope_type, scope_value) VALUES
    ('seed-user-student01',   'class_id',   '1'),
    ('seed-user-student01',   'student_id', '1'),
    ('seed-user-student01',   'student_no', '2023001'),
    ('seed-user-student02',   'class_id',   '1'),
    ('seed-user-student02',   'student_id', '2'),
    ('seed-user-student02',   'student_no', '2023002'),
    ('seed-user-student03',   'class_id',   '2'),
    ('seed-user-student03',   'student_id', '3'),
    ('seed-user-student03',   'student_no', '2023003'),
    ('seed-user-counselor01', 'class_id',   '1'),
    ('seed-user-counselor01', 'class_id',   '2');

-- --- 数据源白名单（管理员无需记录） -------------------------------------------------------------
INSERT IGNORE INTO datasource_access (role, datasource_id) VALUES
    ('student',   'campus'),
    ('counselor', 'campus');

-- --- 示例会话 -----------------------------------------------------------------------------------
INSERT IGNORE INTO conversation (id, user_id, title, datasource_id, model_config_id, created_at, updated_at) VALUES
    ('seed-conv-student01-1',   'seed-user-student01',   '我的数学成绩',     'campus', NULL, 1792458000000, 1792461600000),
    ('seed-conv-student01-2',   'seed-user-student01',   '我的考勤记录',     'campus', NULL, 1792465200000, 1792465200000),
    ('seed-conv-student02-1',   'seed-user-student02',   '我的各科成绩',     'campus', NULL, 1792468800000, 1792472400000),
    ('seed-conv-counselor01-1', 'seed-user-counselor01', '一班各科平均分',   'campus', NULL, 1792476000000, 1792476000000),
    ('seed-conv-admin-1',       'seed-user-admin',       '各班人数统计',     'campus', NULL, 1792479600000, 1792479600000);

-- --- 示例消息（一条完整问答，打开会话即可看到 SQL 与结果） --------------------------------------
INSERT IGNORE INTO message (id, conversation_id, role, content, sql_text, chart_json, rows_json, created_at) VALUES
    ('seed-msg-student01-1-user', 'seed-conv-student01-1', 'user', '我的数学成绩是多少？', NULL, NULL, NULL, 1792458000000),
    ('seed-msg-student01-1-bot',  'seed-conv-student01-1', 'assistant', '你的数学成绩是 92.0 分。',
     'SELECT s.name, c.name AS course_name, sc.score FROM score sc JOIN student s ON s.id = sc.student_id JOIN course c ON c.id = sc.course_id WHERE s.student_no = ''2023001'' AND c.code = ''MATH101''',
     '{"type":"TABLE","xField":"course_name","yField":"score","title":"我的数学成绩"}',
     '[{"name":"张伟","course_name":"数学","score":92.0}]', 1792461600000);
