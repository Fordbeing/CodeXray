<div align="center">

<img src="https://img.icons8.com/fluency/96/source-code.png" width="80" alt="CodeXray Logo"/>

# CodeXray

**AI 驱动的代码分析平台 — 输入仓库链接，自动生成专业分析报告**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![Docker](https://img.shields.io/badge/Docker-一键部署-blue.svg)](docker-compose.yml)

[功能特性](#-功能特性) · [快速开始](#-快速开始) · [架构设计](#-架构设计) · [部署指南](#-部署指南) · [配置说明](#-配置说明)

</div>

---

##  项目简介

CodeXray 是一个基于 LLM + RAG 的智能代码分析平台。只需输入 GitHub 仓库链接，系统会自动克隆代码、进行 AST 级别的语义切片、向量化索引，最终通过多阶段 AI 分析流水线生成包含评分、架构解读、模块分析、改进建议的**专业级代码分析报告**。

分析完成后，你还可以通过**对话式代码问答**深入探索代码细节 — 系统基于 RAG 检索增强生成，精准定位相关代码片段并给出准确回答。

> 适合：技术选型调研、开源项目学习、代码审查、团队技术分享

---

##  功能特性

### 仓库深度分析

输入 GitHub 仓库 URL 或上传代码压缩包，系统自动执行四阶段分析流水线：

| 阶段 | 功能 | 说明 |
|------|------|------|
| **Scanning** | 代码扫描与切片 | AST 语义切片，按 controller/service/model 等分类 |
| **Indexing** | 向量化索引 | 生成 embedding 向量，存入 Elasticsearch |
| **Analyzing** | 模块级分析 | Map-Reduce 模式逐类目分析，虚拟线程并行 |
| **Reporting** | 报告生成 | 综合评分（代码质量/结构/文档/测试/依赖）、改进建议 |

分析报告包含：0-100 综合评分、技术栈识别、模块结构图、优势与不足分析、最终评级。

### 对话式代码问答 (RAG)

基于检索增强生成的智能问答：

- **语义搜索** — 向量检索 + 全文检索双通道，精准定位相关代码
- **多轮对话** — 支持上下文记忆，追问更深入
- **意图路由** — 自动识别代码定位、原理解释、架构概览、优化建议等意图
- **流式输出** — 打字机效果，实时查看 AI 回答
- **代码高亮** — Markdown 渲染 + 代码块一键复制

### GitHub 每日热点

- 自动抓取 GitHub Trending 项目，AI 生成中英文双语分析（定位/架构/应用场景/亮点/学习曲线）
- 按语言筛选，支持查看历史趋势
- 邮件订阅每日热点日报推送

### GitHub 项目概览

- 用户头像 / Bio / 粉丝统计 / 语言分布 / 账龄分析
- 仓库搜索排序 / 加载更多 / 收藏仓库浏览
- Star 排行、原创 vs Fork 分布

### 用户系统与数据管理

- 注册 / 登录（JWT + PBKDF2 密码哈希）
- 个人设置（修改昵称、关联 GitHub、修改密码、邮箱验证）
- 分析历史管理（搜索 / 状态筛选 / 排序 / 分页 / 删除清理）
- 每个用户只能看到自己的分析数据

### 系统设置

- AI 模型配置：支持 9 大服务商（DeepSeek / OpenAI / Claude / 通义千问 / Moonshot / 智谱 / 文心 / Groq / Ollama），或自定义 OpenAI 兼容 API
- 邮箱 SMTP 配置：快捷填入 163 / QQ / Gmail / Outlook
- 配置预设：保存多组 AI/邮箱配置，一键切换
- 自动保存 + 连接测试

---

##  快速开始

### 一键部署（推荐）

确保已安装 [Docker](https://www.docker.com/products/docker-desktop/) 和 Docker Compose，然后执行：

```bash
git clone https://github.com/Fordbeing/CodeXray.git
cd CodeXray
bash deploy.sh
```

脚本会自动：检查环境 → 构建镜像 → 启动 6 个服务 → 等待就绪 → 显示访问地址。

部署完成后访问 **http://localhost**，首次使用请在「系统设置」中配置 AI 模型。

### 手动启动（开发环境）

**后端：**

```bash
# 需要本地运行 MySQL 3306、Redis 6379、Elasticsearch 9200、MinIO 9000
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端：**

```bash
cd frontend
npm install
npm run dev
```

---

##  架构设计

```
                         ┌─────────────────────────────────┐
                         │        Vue 3 + Element Plus     │
                         │         (Nginx :80)             │
                         └──────────┬──────────────────────┘
                                    │ /api/*
                         ┌──────────▼──────────────────────┐
                         │      Spring Boot 3.3 (Java 21)  │
                         │         REST API :8081           │
                         │                                  │
                         │  ┌──────────┐  ┌──────────────┐ │
                         │  │ JWT Auth │  │ 分析流水线    │ │
                         │  │Interceptor│  │ (虚拟线程并行)│ │
                         │  └──────────┘  └──────┬───────┘ │
                         │                        │         │
                         │  ┌─────────────────────▼──────┐ │
                         │  │    Agent Pipeline (4 阶段)  │ │
                         │  │ Scanner → Indexer →        │ │
                         │  │ Analyzer → Reporter         │ │
                         │  └──┬──────────┬──────────┬───┘ │
                         │     │          │          │      │
                         │  ┌──▼───┐  ┌──▼───┐  ┌──▼───┐ │
                         │  │ JGit │  │ LLM  │  │ RAG  │ │
                         │  │Clone │  │Client│  │Query │ │
                         │  └──────┘  └──────┘  └──────┘ │
                         └──┬────┬────┬────┬──────────────┘
                            │    │    │    │
               ┌────────────┘    │    │    └────────────┐
               │                 │    │                  │
        ┌──────▼──────┐  ┌──────▼──┐ │  ┌──────────────▼┐
        │   MySQL 8   │  │  Redis 7 │ │  │      MinIO    │
        │  业务数据    │  │  缓存    │ │  │  对象存储     │
        └─────────────┘  └─────────┘ │  └───────────────┘
                              ┌──────▼────────┐
                              │ Elasticsearch │
                              │  向量检索     │
                              └───────────────┘
```

### RAG 查询流程

```
用户提问 → QueryRouter(意图分类+关键词提取)
                ↓
        EmbeddingService(文本向量化)
                ↓
    ┌───────────┴───────────┐
    │                       │
向量检索(kNN=6)      全文检索(Multi-match=4)
    │                       │
    └───────────┬───────────┘
                ↓
         去重 + ContextBuilder(组装上下文, ≤15K chars)
                ↓
         LLM 生成回答 → 流式返回前端
```

### 分析流水线

```
GitHub URL → GitClone(浅克隆)
                ↓
    ┌───────────┴───────────┐
    │                       │
ScannerAgent          IndexerAgent
(代码切片+向量化)     (配置解析+技术栈识别)
    │                       │
    └───────────┬───────────┘
                ↓
         AnalyzerAgent(7类目 Map-Reduce 并行分析)
                ↓
         ReporterAgent(综合报告生成)
                ↓
         完成 → 评分/模块/建议/评级
```

---

##  技术栈

### 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 编程语言（虚拟线程） |
| Spring Boot | 3.3.5 | Web 框架 |
| MyBatis-Plus | 3.5.7 | ORM |
| MySQL | 8.0 | 业务数据存储 |
| Redis | 7.x | 缓存 / 会话 / 限流 |
| Elasticsearch | 8.13 | 向量检索 + 全文搜索 |
| MinIO | latest | 对象存储 |
| JGit | 6.10 | 纯 Java Git 实现 |
| jjwt | 0.12.6 | JWT 认证 |
| Jsoup | 1.18 | HTML 解析（热点抓取） |

### 前端

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| Vite | latest | 构建工具 |
| Element Plus | 2.9 | UI 组件库 |
| Vue Router | 4.5 | 路由 |
| Axios | 1.7 | HTTP 客户端 |
| marked.js | latest | Markdown 渲染 |

### 基础设施

| 组件 | 说明 |
|------|------|
| Docker Compose | 一键部署 6 个服务 |
| Nginx | 前端托管 + API 反向代理 |
| 多阶段构建 | Maven → JRE, Node → Nginx |

---

##  部署指南

### Docker Compose 服务

| 服务 | 端口 | 说明 |
|------|------|------|
| `codexray-frontend` | 80 | Nginx 静态托管 + API 代理 |
| `codexray-backend` | 8081 | Spring Boot 应用 |
| `codexray-mysql` | 3306 | MySQL 8.0（自动初始化表结构） |
| `codexray-redis` | 6379 | Redis 7（AOF 持久化） |
| `codexray-es` | 9200 | Elasticsearch 8.13 |
| `codexray-minio` | 9000 | MinIO 对象存储 |

### 常用命令

```bash
# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 停止服务
docker compose down

# 重启服务
docker compose restart
```

---

##  配置说明

### AI 模型配置

在页面「系统设置」中配置，支持以下方式：

| 方式 | 说明 |
|------|------|
| 内置服务商 | DeepSeek、OpenAI、Claude、通义千问、Moonshot、智谱、文心、Groq、Ollama |
| 自定义 | 任何兼容 OpenAI 或 Claude API 的服务地址 |

支持**配置预设**：保存多组 API Key + 模型组合，一键切换。

### Embedding 模型

默认使用 `text-embedding-3-small`（768 维）。可在设置中自定义 Embedding 模型和服务地址。

API 不可用时自动降级为哈希向量，60 秒后自动重试恢复。

### 环境变量

可通过环境变量覆盖默认配置（Docker 部署时使用）：

```bash
# AI 模型
LLM_BASE_URL=https://api.deepseek.com
LLM_API_KEY=sk-xxx
LLM_MODEL=deepseek-chat

# 邮件
MAIL_HOST=smtp.163.com
MAIL_PORT=465
MAIL_USERNAME=your@163.com
MAIL_PASSWORD=your-auth-code

# 数据库
MYSQL_PASSWORD=codexray123
```

---

##  项目结构

```
CodeXray/
├── src/main/java/com/codexray/
│   ├── controller/              # REST API 端点（7 个控制器）
│   ├── service/                 # 业务逻辑层
│   ├── agent/                   # 分析流水线（4 阶段 Agent）
│   ├── rag/                     # RAG 检索层
│   │   ├── CodeChunker.java     # 代码语义切片
│   │   ├── EmbeddingService.java# 向量化服务
│   │   ├── VectorStoreService.java # ES 向量存储
│   │   ├── QueryRouter.java     # 意图路由
│   │   └── ContextBuilder.java  # 上下文组装
│   ├── llm/                     # LLM 客户端（兼容 OpenAI/Claude）
│   ├── mapper/                  # MyBatis-Plus Mapper
│   ├── model/                   # 实体 + DTO
│   ├── common/                  # 通用工具（JWT、Result）
│   └── config/                  # 配置类
├── src/main/resources/
│   ├── schema.sql               # 数据库表结构（8 张表）
│   └── application*.yml         # 配置文件
├── frontend/src/
│   ├── views/                   # 页面（7 个视图）
│   ├── components/              # 组件（登录/个人中心弹窗）
│   ├── api/                     # API 客户端（37 个接口函数）
│   ├── layout/                  # 布局（侧边栏 + 用户卡片）
│   └── router/                  # 路由配置
├── docker-compose.yml           # 6 服务编排
├── Dockerfile                   # 后端多阶段构建
├── frontend/Dockerfile          # 前端多阶段构建
└── deploy.sh                    # 一键部署脚本
```

---

##  截图

> 部署后截图展示区 — 建议添加以下页面截图：

- **仪表盘** — 欢迎卡片 + 统计 + 最近分析 + 热点速览
  <img width="1905" height="896" alt="仪表盘" src="https://github.com/user-attachments/assets/2c966759-840e-41fb-a329-c67f7fa8edbd" />
- **仓库分析** — 分析报告（评分雷达图 + 模块分析 + 改进建议）
  <img width="1905" height="896" alt="仓库分析" src="https://github.com/user-attachments/assets/14142382-1a2f-4c3d-a5cc-4ca490ff0b8e" />
- **代码问答** — RAG 对话界面（Markdown 渲染 + 代码高亮）
  <img width="1905" height="896" alt="代码回答" src="https://github.com/user-attachments/assets/848e419e-9653-42cf-b171-e2b1bc3d85f4" />
- **热点推送** — GitHub Trending 卡片 + AI 分析 + 语言分布
  <img width="1905" height="896" alt="热点推送" src="https://github.com/user-attachments/assets/fcb2cc21-8644-443a-b243-e0eec594d88c" />
- **系统设置** — AI 模型配置（服务商选择 + 连接测试）
  <img width="1905" height="896" alt="系统设置" src="https://github.com/user-attachments/assets/17da19e4-60f1-4935-bc12-24f10ff519fb" />
---

##  核心亮点

- **四阶段 Agent 流水线** — 扫描 → 索引 → 分析 → 报告，Java 21 虚拟线程并行加速
- **RAG 检索增强** — 向量检索 + 全文检索双通道，意图路由智能分发
- **多 LLM 适配** — 一套代码兼容 OpenAI / Claude / DeepSeek / 通义千问等 9 大服务商
- **语义代码切片** — 按 class/function 边界智能分割，支持 25+ 文件格式
- **一键 Docker 部署** — 6 个服务一键启动，开箱即用
- **数据持久化** — MySQL + Redis + ES + MinIO 四件套，分析结果不丢失
- **用户系统** — JWT 无状态认证，数据隔离，邮箱验证

---

##  License

本项目基于 [Apache License 2.0](LICENSE) 开源。

---

##  贡献

欢迎提交 Issue 和 Pull Request！

如果你觉得这个项目有帮助，请给一个 Star 支持一下！
