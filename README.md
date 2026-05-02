<div align="center">

<img src="https://img.icons8.com/fluency/96/source-code.png" width="80" alt="CodeXray Logo"/>

# CodeXray

**AI 驱动的智能代码分析平台 — 粘贴仓库链接，秒出专业分析报告**

一个基于 **LLM + RAG** 的代码分析工具，支持 25+ 编程语言，自动生成评分、架构解读、模块分析、改进建议，并提供**对话式代码问答**深入探索代码细节。

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![Docker](https://img.shields.io/badge/Docker-一键部署-blue.svg)](docker-compose.yml)
[![GitHub Stars](https://img.shields.io/github/stars/Fordbeing/CodeXray?style=social)](https://github.com/Fordbeing/CodeXray)

[功能特性](#功能特性) · [在线演示](#在线演示) · [快速开始](#快速开始) · [架构设计](#架构设计) · [部署指南](#部署指南) · [配置说明](#配置说明)

</div>

---

## 为什么选择 CodeXray？

面对一个陌生的开源项目，你是否遇到过这些问题：

- 阅读几千行代码，不知从何入手？
- 想评估项目质量，却没有快速方法？
- 想了解架构设计，却缺少专业分析？
- 有具体问题，但搜索文档找不到答案？

**CodeXray** 帮你解决这些痛点 — 粘贴一个链接，几分钟内获得专业级分析报告，还能通过对话随时追问代码细节。

---

## 功能特性

### 仓库深度分析

粘贴 GitHub 仓库 URL 或上传代码压缩包，系统自动执行四阶段流水线：

```
提交任务 → AI 预检 → 仓库克隆 → 代码扫描 & 向量索引（虚拟线程并行）
                                    ↓
                          7 类目 Map-Reduce 并行分析
                                    ↓
                          综合评分 + 改进建议报告
```

报告内容：**0-100 综合评分**（代码质量 / 项目结构 / 文档 / 测试 / 依赖管理）+ 技术栈识别 + 模块结构 + 优势与不足 + 最终评级。

### 对话式代码问答

基于 RAG 检索增强生成，支持：

| 特性 | 说明 |
|------|------|
| 语义搜索 | 向量检索 + 全文检索双通道，精准定位相关代码 |
| 多轮对话 | 上下文记忆，追问更深入 |
| 意图路由 | 自动识别代码定位 / 原理解释 / 架构概览 / 优化建议 |
| 流式输出 | 打字机效果，实时查看 AI 回答 |
| 代码高亮 | Markdown 渲染 + 代码块一键复制 |

### GitHub 每日热点 & 项目概览

- 自动抓取 Trending 项目，AI 生成中英文双语分析（定位 / 架构 / 应用场景 / 学习曲线）
- 按语言筛选，支持查看历史趋势
- 邮件订阅每日热点日报推送
- 用户画像（头像 / Bio / 粉丝统计 / 语言分布 / 账龄分析）、仓库搜索排序、Star 排行

### 用户系统 & 数据管理

- 注册 / 登录（JWT + PBKDF2 密码哈希），数据隔离
- 个人设置（修改昵称、关联 GitHub、修改密码）
- 分析历史管理（搜索 / 状态筛选 / 排序 / 分页 / 删除清理）

### 系统设置

- AI 模型：支持 9 大服务商（DeepSeek / OpenAI / Claude / 通义千问 / Moonshot / 智谱 / 文心 / Groq / Ollama）或自定义 API
- 邮箱 SMTP：快捷填入 163 / QQ / Gmail / Outlook
- 配置预设：保存多组配置，一键切换，自动保存

---

## 快速开始

### 一键部署（推荐）

```bash
git clone https://github.com/Fordbeing/CodeXray.git && cd CodeXray && bash deploy.sh
```

> 需要 [Docker](https://www.docker.com/products/docker-desktop/) + Docker Compose。脚本自动完成：检查环境 → 构建镜像 → 启动 6 个服务 → 等待就绪。

访问 **http://localhost**，首次使用请在「系统设置」中配置 AI 模型（推荐 DeepSeek，性价比最高）。

<details>
<summary>手动启动（开发环境）</summary>

**后端**（需要本地运行 MySQL 3306、Redis 6379、Elasticsearch 9200、MinIO 9000）：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端：**

```bash
cd frontend && npm install && npm run dev
```

</details>

---

## 在线演示

<details>
<summary>点击展开截图</summary>

**仪表盘** — 欢迎卡片 + 统计 + 最近分析 + 热点速览

<img width="1905" height="896" alt="仪表盘" src="https://github.com/user-attachments/assets/7e633603-6c0c-4f75-abe3-571108a5f6dc" />

**仓库分析** — 分析报告（评分雷达图 + 模块分析 + 改进建议）

<img width="1905" height="896" alt="仓库分析" src="https://github.com/user-attachments/assets/57bc5936-77c5-44ad-86ac-45c011a12dda" />

**代码问答** — RAG 对话界面（Markdown 渲染 + 代码高亮）

<img width="1905" height="896" alt="代码回答" src="https://github.com/user-attachments/assets/c305dcba-1ea3-4cee-8adf-db83640e4df1" />

**热点推送** — GitHub Trending 卡片 + AI 分析 + 语言分布

<img width="1905" height="896" alt="热点推送" src="https://github.com/user-attachments/assets/83ccab74-662e-4dfe-a5ae-8ebb3871b77e" />

**系统设置** — AI 模型配置（服务商选择 + 连接测试）

<img width="1905" height="896" alt="系统设置" src="https://github.com/user-attachments/assets/cb15ce0d-8b68-4f43-8ff8-2037420a3daa" />

</details>

---

## 架构设计

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

## 技术栈

| 类别 | 技术 |
|------|------|
| **后端** | Java 21 (虚拟线程) · Spring Boot 3.3 · MyBatis-Plus 3.5 |
| **存储** | MySQL 8.0 · Redis 7 · Elasticsearch 8.13 · MinIO |
| **前端** | Vue 3.5 · Vite · Element Plus 2.9 · marked.js |
| **AI** | 多 LLM 适配 (OpenAI/Claude/DeepSeek/...) · text-embedding-3-small |
| **部署** | Docker Compose · Nginx · 多阶段构建 |

---

## 部署指南

| 服务 | 端口 | 说明 |
|------|------|------|
| `codexray-frontend` | 80 | Nginx 静态托管 + API 代理 |
| `codexray-backend` | 8081 | Spring Boot 应用 |
| `codexray-mysql` | 3306 | MySQL 8.0（自动建表） |
| `codexray-redis` | 6379 | Redis 7（AOF 持久化） |
| `codexray-es` | 9200 | Elasticsearch 8.13 |
| `codexray-minio` | 9000 | MinIO 对象存储 |

<details>
<summary>常用命令</summary>

```bash
docker compose ps          # 查看状态
docker compose logs -f     # 查看日志
docker compose down        # 停止服务
docker compose restart     # 重启服务
```

</details>

---

## 配置说明

在页面「系统设置」中完成配置：

- **AI 模型**：9 大内置服务商或自定义 OpenAI/Claude 兼容 API，支持配置预设一键切换
- **Embedding**：默认 `text-embedding-3-small`（768 维），不可用时自动降级为哈希向量
- **邮箱 SMTP**：快捷填入 163 / QQ / Gmail / Outlook

<details>
<summary>环境变量（Docker 部署时使用）</summary>

```bash
LLM_BASE_URL=https://api.deepseek.com    # API 地址
LLM_API_KEY=sk-xxx                        # API Key
LLM_MODEL=deepseek-chat                   # 模型名称
MAIL_HOST=smtp.163.com                    # SMTP 服务器
MAIL_USERNAME=your@163.com                # 邮箱账号
MAIL_PASSWORD=your-auth-code              # 邮箱密码
MYSQL_PASSWORD=codexray123                # 数据库密码
```

</details>

---

## 项目结构

<details>
<summary>点击展开</summary>

```
CodeXray/
├── src/main/java/com/codexray/
│   ├── controller/         # REST API 端点（7 个控制器）
│   ├── service/            # 业务逻辑层
│   ├── agent/              # 分析流水线（4 阶段 Agent）
│   ├── rag/                # RAG 检索层（切片/向量/路由/上下文）
│   ├── llm/                # LLM 客户端（兼容 OpenAI/Claude）
│   ├── mapper/             # MyBatis-Plus Mapper
│   ├── model/              # 实体 + DTO
│   ├── common/             # 通用工具（JWT、Result）
│   └── config/             # 配置类
├── frontend/src/
│   ├── views/              # 页面（7 个视图）
│   ├── components/         # 组件（登录/个人中心弹窗）
│   ├── api/                # API 客户端
│   └── layout/             # 布局（侧边栏 + 用户卡片）
├── docker-compose.yml      # 6 服务编排
├── Dockerfile              # 后端多阶段构建
└── deploy.sh               # 一键部署脚本
```

</details>

---

## 核心亮点

- **四阶段 Agent 流水线** — 扫描 → 索引 → 分析 → 报告，Java 21 虚拟线程并行加速
- **RAG 检索增强** — 向量检索 + 全文检索双通道，意图路由智能分发
- **多 LLM 适配** — 一套代码兼容 OpenAI / Claude / DeepSeek / 通义千问等 9 大服务商
- **语义代码切片** — 按 class/function 边界智能分割，支持 25+ 文件格式
- **一键 Docker 部署** — 6 个服务一键启动，开箱即用
- **数据持久化** — MySQL + Redis + ES + MinIO 四件套，分析结果不丢失
- **用户系统** — JWT 无状态认证，数据隔离，邮箱验证

---

## License

[Apache License 2.0](LICENSE)

---

## 贡献

欢迎提交 [Issue](https://github.com/Fordbeing/CodeXray/issues) 和 [Pull Request](https://github.com/Fordbeing/CodeXray/pulls)！

如果这个项目对你有帮助，点个 **Star** 支持一下！

[![Star History Chart](https://api.star-history.com/svg?repos=Fordbeing/CodeXray&type=Date)](https://star-history.com/#Fordbeing/CodeXray&Date)
