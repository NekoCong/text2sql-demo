# 部署与恢复手册（云服务器全栈 Docker）

> 这份文档是写给**下一个来接手的人或 AI** 的。设计原则只有一条：
> **恢复所需的一切都在这个 git 仓库里，不依赖服务器上的任何状态。**
>
> 服务器是**抢占式实例**，随时可能被回收并重开成一台空机器。所以"恢复"不是一个特殊流程，
> 它就是本文档的正常流程 —— 重新跑一遍即可。

---

## 1. 架构与端口暴露

```
                    ┌──────────────────────────── 公网只开 22 / 80 ───────────────────────────┐
浏览器 ──80──▶ frontend (nginx:alpine)                                                       │
                    │  /            → 静态 SPA                                                  │
                    │  /api/        → proxy_pass http://backend:8080  （含 SSE，关缓冲）        │
                    ▼                                                                          │
                backend (eclipse-temurin:25-jre-alpine)  ── R2DBC ──▶ mysql:8.4               │
                    │                                                      127.0.0.1:3306       │
                    └── OTLP ──▶ otel-lgtm (Grafana/Tempo/Prometheus/Loki)                    │
                                 127.0.0.1:3000 / 4317 / 4318 / 9090                           │
                    └──────────────────────────────────────────────────────────────────────────┘
```

| 服务 | 镜像 | 端口 | 对公网 |
| --- | --- | --- | --- |
| `frontend` | `nginx:alpine` | `80` | ✅ 唯一入口 |
| `backend` | `eclipse-temurin:25-jre-alpine` | `8080`（仅容器网络） | ❌ |
| `mysql` | `mysql:8.4` | `127.0.0.1:3306` | ❌ |
| `otel-lgtm` | `grafana/otel-lgtm:latest` | `127.0.0.1:3000/4317/4318/9090` | ❌ |

前端镜像里**没有写死任何 IP**：`src/api/http.ts` 的 `baseURL` 是 `/api`，由 nginx 同源反代。
所以实例换 IP、换域名都不需要重新构建。

---

## 2. 前置条件

### 2.1 实例规格

| 项 | 建议 |
| --- | --- |
| vCPU / 内存 | **≥ 4 vCPU / 8 GB**（`otel-lgtm` 本身就不轻，且首次要在服务器上跑 Maven + Vite 构建） |
| 最低可用 | 2 vCPU / 4 GB，但首次构建会很慢，且可能 OOM |
| 磁盘 | ≥ 40 GB（镜像 + Maven/Node 依赖缓存） |
| 系统 | Ubuntu 22.04+ / Debian 12+ / RHEL 系（脚本会自适应包管理器） |

> 内存不够时的替代方案：本地构建镜像后 `docker save | ssh ... docker load`，或在服务器上临时
> 加大 swap。但注意**本机没有装 Docker**，所以本地构建这条路目前走不通。

### 2.2 安全组（必须）

入方向**只放行**：

- `22/tcp`（SSH）
- `80/tcp`（HTTP）

**不要**放行 `3306`、`3000`、`4317`、`4318`、`9090`、`2375`、`2376`。

需要 Grafana / 直连 MySQL 时用 SSH 隧道：

```bash
ssh -L 3000:127.0.0.1:3000 -L 9090:127.0.0.1:9090 -L 3306:127.0.0.1:3306 <user>@<host>
# 然后本地浏览器打开 http://localhost:3000
```

### 2.3 代码

仓库远端：`https://github.com/NekoCong/text2sql-demo.git`

服务器上执行前，**先把本地改动推到远端**，否则服务器 clone 到的还是旧代码。

---

## 3. 从零部署

```bash
# 1) 装 git（若镜像没有）并拉代码
sudo apt-get update && sudo apt-get install -y git    # RHEL 系用 dnf
git clone https://github.com/NekoCong/text2sql-demo.git
cd text2sql-demo

# 2) 准备密钥
cp deploy/.env.example deploy/.env
# 生成一个强 JWT 密钥：
openssl rand -base64 48
# 编辑 deploy/.env，至少填好 MYSQL_ROOT_PASSWORD 和 TEXT2SQL_JWT_SECRET
# DASHSCOPE_API_KEY 可留空（留空则登录/会话管理可用，但 LLM 问答不可用）

# 3) 一条命令拉起全部
sudo bash deploy/bootstrap.sh
```

`bootstrap.sh` 做的事，按顺序：

1. 装基础包（`ca-certificates` / `curl` / `git`）
2. 装 Docker Engine + compose plugin（官方脚本）
3. 写 `/etc/docker/daemon.json`：日志轮转 10MB×3；**故意不配 `hosts`，守护进程不监听 TCP**
4. `ufw` 只放行 22 / 80
5. 校验 `deploy/.env`：缺 `MYSQL_ROOT_PASSWORD` 或 `TEXT2SQL_JWT_SECRET`、或 JWT 密钥短于 32 字符 → **直接退出**，绝不写默认值
6. `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build`
7. 轮询 `http://127.0.0.1/api/text2sql/health` 直到就绪（最多 5 分钟），失败则打印排查命令
8. 打印访问地址与账号

脚本**幂等**：重复执行安全，这正是实例被回收后的恢复方式。

---

## 4. 验证清单

```bash
# 容器状态：mysql 必须是 healthy，其余 running
docker compose -f deploy/docker-compose.prod.yml ps

# 只应看到 22 / 80 对公网监听（3306 等只能看到 127.0.0.1）
sudo ss -lntp

# 后端存活
curl -s http://127.0.0.1/api/text2sql/health          # → OK

# 前端
curl -sI http://127.0.0.1/ | head -1                  # → HTTP/1.1 200 OK

# 种子数据 + 角色是否真的生效（用 alice 登录）
curl -s -X POST http://127.0.0.1/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"alice","password":"alice123"}'
# 响应里应含 "role":"user"；用返回的 token 调 /api/conversations 应只看到 2 条（都是 alice 的）
```

浏览器端到端：

1. `http://<服务器IP>/` → 用 `admin/admin123` 登录
2. 左侧会话列表应看到 **4 条**（alice 2 条 + bob 1 条 + admin 1 条）
3. 退出，用 `alice/alice123` 登录 → 只看到 **2 条**
4. 用 alice 提问 → SSE 流式返回，不中断（验证 nginx 的 SSE 配置）
5. 退出，`bob/bob123` → 只看到 **1 条**

---

## 5. 抢占式实例被回收后怎么恢复

**这是常规操作，不是应急。**

```bash
# 新机器上（IP 可能已经变了）
git clone https://github.com/NekoCong/text2sql-demo.git
cd text2sql-demo
cp deploy/.env.example deploy/.env   # 重新填密钥
sudo bash deploy/bootstrap.sh
```

关于数据：**MySQL 数据不持久化，会被一起丢掉**。这是有意为之：

- 应用库的所有数据都由 `db/app-schema.sql` + `db/app-data.sql` 在 MySQL 容器**首次初始化**时重建
- 演示目标库（`demo`）是 H2 内存库，每次启动自动灌 `db/schema.sql` + `db/data.sql`
- 所以恢复后立刻就是可演示状态，不需要备份恢复流程

如果以后需要保留真实数据，再引入独立云盘挂载 `/var/lib/mysql`，或定期 `mysqldump`。

> ⚠️ 实例重开后**公网 IP 可能变化**（本次已确认不绑弹性公网 IP）。前端同源反代不受影响，
> 但书签和外部引用会失效。需要固定入口时再绑弹性公网 IP。

---

## 6. 故障排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `bootstrap.sh` 报 `These keys are empty` | `deploy/.env` 没填 | 填好 `MYSQL_ROOT_PASSWORD` / `TEXT2SQL_JWT_SECRET` |
| backend 启动即退出，日志有 `R2dbcPermissionDeniedException` | **给 R2DBC URL 加了 `sslMode=DISABLED`** | 去掉它。MySQL 8.4 的 root 用 `caching_sha2_password`，`r2dbc-mysql` 默认 `sslMode=PREFERRED` 靠 TLS 完成握手；r2dbc-mysql **没有** `allowPublicKeyRetrieval` 选项，禁用 TLS 就必然认证失败 |
| backend 连不上 mysql | mysql 还没健康 | compose 里已配 `depends_on: condition: service_healthy`；若仍失败看 `docker compose logs mysql` |
| 首次构建 OOM / 卡死 | 内存不足 | 升配，或临时加 swap，或先只 `up -d mysql otel-lgtm`，再单独 build |
| 登录返回 401 但账号密码没错 | `TEXT2SQL_JWT_SECRET` 被改过，旧 token 失效 | 重新登录；密钥变更会使所有既有会话失效 |
| 页面能开但接口 502 | backend 未就绪或崩了 | `docker compose -f deploy/docker-compose.prod.yml logs --tail=200 backend` |
| 提问时 SSE 中途断流 | nginx 缓冲/超时 | `nginx.conf` 里已设 `proxy_buffering off; proxy_read_timeout 3600s;`，确认容器用的是仓库里的这份配置 |
| 种子数据没进库 | MySQL 卷已存在，init 脚本不会再跑 | `docker compose -f deploy/docker-compose.prod.yml down -v` 再 up（**会删库**） |
| 改了 `app-schema.sql` 但表结构没变 | 同上：init 脚本只在空库时执行 | 手工 `ALTER TABLE`，或 `down -v` 重建 |

---

## 7. 本地开发时的一个坑

本机 `JAVA_HOME` 指向 JDK 11，而项目要求 Java 25，直接 `mvnw` 会报
`不支持发行版本 25`。本机已装 JDK 25，构建时指定即可：

```powershell
$env:JAVA_HOME = "C:\Users\14364\.jdks\corretto-25.0.4.1"
.\mvnw.cmd test
```

Docker 构建不受影响（镜像里自带 JDK 25）。

---

## 8. 相关文件

| 文件 | 作用 |
| --- | --- |
| `deploy/bootstrap.sh` | 幂等的服务器初始化脚本 |
| `deploy/docker-compose.prod.yml` | 全栈编排（MySQL + otel + backend + frontend） |
| `deploy/.env.example` | 密钥模板；`deploy/.env` 已 git-ignore |
| `text2sql-backend/Dockerfile` | 多阶段：`maven:3.9-eclipse-temurin-25` 构建 → `eclipse-temurin:25-jre-alpine` 运行（非 root） |
| `text2sql-frontend/Dockerfile` | `node:22-alpine` 构建 → `nginx:alpine` 托管 |
| `text2sql-frontend/nginx.conf` | SPA 回退 + `/api` 反代 + SSE 关缓冲 |
| `docker-compose.yml` | **本地**只用基础设施（MySQL + otel-lgtm），不含应用容器 |
| `text2sql-backend/src/main/resources/db/app-data.sql` | 种子数据：admin / alice / bob + 会话 |
