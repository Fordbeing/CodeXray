<div align="center">

<img src="https://img.icons8.com/fluency/96/source-code.png" width="80" alt="CodeXray Logo"/>

# CodeXray

**AI 驱动的开源代码分析平台 — 粘贴仓库链接，秒出专业级分析报告**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![Docker](https://img.shields.io/badge/Docker-一键部署-blue.svg)](docker-compose.yml)
[![GitHub Stars](https://img.shields.io/github/stars/Fordbeing/CodeXray?style=social)](https://github.com/Fordbeing/CodeXray)

**支持 25+ 编程语言 · 4 阶段 Agent 流水线 · RAG 代码问答 · AI Code Review · GitHub 热点追踪**

[功能特性](#功能特性) · [效果展示](#效果展示) · [快速开始](#快速开始) · [架构设计](#架构设计) · [技术栈](#技术栈) · [部署指南](#部署指南)

</div>

---

## 痛点：你是否也遇到过这些问题？

> 面对一个几千 Star 的开源项目，想快速了解它的架构和代码质量，却不知从何入手？
>
> 想评估一个项目的代码水平，只能手动翻阅几十个文件？
>
> 阅读源码时遇到疑问，搜索文档和 Stack Overflow 却找不到答案？

**CodeXray 只需一个 GitHub 链接，几分钟内帮你完成这一切。**

---

## 功能特性

### 仓库深度分析

粘贴 GitHub 仓库 URL 或上传代码压缩包，系统自动执行四阶段智能流水线：

```
提交任务 → AI 预检 → 仓库克隆 → 代码扫描 & 向量索引（虚拟线程并行）
                                    ↓
                          7 类目 Map-Reduce 并行分析
                                    ↓
                        综合评分 + 改进建议 + 最终评级
```

报告内容覆盖 **8 个维度的 0-100 评分**（代码质量 / 项目结构 / 文档完善度 / 测试覆盖 / 依赖管理 / 安全性 / 性能 / 可维护性）+ 技术栈识别 + 模块结构分析 + 优势与不足 + 改进建议。

### RAG 对话式代码问答

基于 **向量检索 + 全文检索双通道** 的 RAG 检索增强生成，不只是简单的问答：

| 特性 | 说明 |
|------|------|
| 语义搜索 | 向量 kNN + Multi-match 全文检索，双通道精准定位相关代码 |
| ReAct Agent | 推理 → 行动 → 观察循环，自主选择 4 种工具（代码搜索 / 文件阅读 / 符号查找 / 依赖分析） |
| 质量反思 | LLM 自动评估回答质量（1-10 分），低于 7 分自动重试 |
| 意图路由 | 自动识别代码定位 / 原理解释 / 架构概览 / 优化建议 |
| 流式输出 | SSE 打字机效果，实时查看 AI 回答 |
| 代码高亮 | Markdown 渲染 + 代码块一键复制 + 对话导出 |

### AI Code Review

提交 Git Diff 或直接审查已有分析的文件，AI 从 **5 个维度** 逐 Hunk 审查：

- **正确性** — 逻辑错误、边界条件、空指针
- **安全性** — 注入攻击、敏感信息泄露、权限绕过
- **性能** — N+1 查询、内存泄漏、算法复杂度
- **可读性** — 命名规范、代码结构、注释质量
- **最佳实践** — 设计模式、异常处理、API 设计

### 报告对比

对同一仓库的两次分析结果进行 **AI 驱动的差异对比**，直观查看代码质量变化趋势，适合版本迭代后的质量回归。

### 代码浏览器

分析完成后，直接在浏览器中 **浏览仓库文件树**、查看源码、全文搜索代码，无需离开平台。

### GitHub 每日热点 & 项目概览

- 自动抓取 GitHub Trending 项目，AI 生成 **中英文双语分析**（项目定位 / 架构解读 / 应用场景 / 学习曲线）
- 按语言筛选，Top 3 高亮面板展示
- **邮件订阅** 每日热点日报推送
- GitHub 用户画像（头像 / Bio / 粉丝统计 / 语言分布 / 账龄分析）、仓库搜索排序、Star 排行

### 灵活的系统设置

- **AI 模型**：内置 9 大服务商预设（DeepSeek / OpenAI / Claude / 通义千问 / Moonshot / 智谱 / 文心 / Groq / Ollama），支持自定义 API，配置预设一键切换
- **邮箱 SMTP**：快捷填入 163 / QQ / Gmail / Outlook
- **连接测试**：一键测试 AI 模型和 Embedding API 连通性

### 用户系统 & 数据管理

- 注册 / 登录（JWT + PBKDF2 密码哈希），数据隔离
- 个人设置（修改昵称、关联 GitHub 用户名、修改密码）
- 分析历史管理（搜索 / 状态筛选 / 排序 / 分页 / 删除时自动清理 ES + MinIO 数据）

---

## 效果展示

### 仪表盘 — 欢迎卡片 + 统计概览 + 最近分析 + 热点速览

<img width="1905" height="896" alt="仪表盘" src="https://github.com/user-attachments/assets/7e633603-6c0c-4f75-abe3-571108a5f6dc" />

### 仓库分析 — 评分雷达图 + 模块分析 + 改进建议

<img width="1905" height="896" alt="仓库分析" src="https://github.com/user-attachments/assets/950f6105-0a21-4cce-a49d-b7dd4da95958" />

### 代码问答 — RAG 对话（秒级响应 + Markdown 渲染 + 代码高亮 + 流式输出）

<img width="1905" height="896" alt="代码回答" src="https://github.com/user-attachments/assets/c305dcba-1ea3-4cee-8adf-db83640e4df1" />

### AI代码审查 — git diff 审查 + 文件审查 + 审查历史

<img width="1905" height="896" alt="AI代码审查" src="https://github.com/user-attachments/assets/8537603d-3802-40d6-895c-4f39e8b5a575" />

### AI报告对比 — 新旧报告对比 + 对比历史

<img width="1905" height="896" alt="报告对比" src="https://github.com/user-attachments/assets/e2f005ad-a49e-43b1-aef1-b8ec5d9848eb" />

### 热点推送 — GitHub Trending 卡片 + AI 中英文分析 + 语言分布

<img width="1905" height="896" alt="热点推送" src="https://github.com/user-attachments/assets/83ccab74-662e-4dfe-a5ae-8ebb3871b77e" />

### 系统设置 — 9 大 AI 服务商预设 + 连接测试

<img width="1905" height="896" alt="系统设置" src="https://github.com/user-attachments/assets/cb15ce0d-8b68-4f43-8ff8-2037420a3daa" />

---

## 快速开始

### 一键部署（推荐）

```bash
git clone https://github.com/Fordbeing/CodeXray.git && cd CodeXray && bash deploy.sh
```

> 需要 [Docker](https://www.docker.com/products/docker-desktop/) + Docker Compose。脚本自动完成：检查环境 → 构建镜像 → 启动 6 个服务 → 等待就绪。

访问 **http://localhost**，首次使用请在「系统设置」中配置 AI 模型（推荐 DeepSeek，性价比最高）。

### 手动启动（开发环境）

**后端**（需要本地运行 MySQL 3306、Redis 6379、Elasticsearch 9200、MinIO 9000）：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端：**

```bash
cd frontend && npm install && npm run dev
```

---

## 架构设计

### 整体架构

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

### Agent 分析流水线

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
         PlannerAgent(LLM 动态规划执行步骤)
                ↓
         AnalyzerAgent(7 类目 Map-Reduce 并行分析)
                ↓
         ReporterAgent(综合报告生成)
                ↓
         完成 → 评分/模块/建议/评级
```

### RAG 查询流程

```
用户提问 → QueryRouter(意图分类 + 关键词提取)
                ↓
        EmbeddingService(文本向量化, Redis 缓存)
                ↓
    ┌───────────┴───────────┐
    │                       │
向量检索(kNN=6)      全文检索(Multi-match=4)
    │                       │
    └───────────┬───────────┘
                ↓
         去重 + ContextBuilder(组装上下文, ≤15K chars)
                ↓
    ┌───────────┴───────────┐
    │                       │
LLM 生成回答          ReflectionService
(ReAct 循环,4轮)     (质量评分, <7分重试)
    │                       │
    └───────────┬───────────┘
                ↓
         SSE 流式返回前端
```

---

## 技术栈

| 类别 | 技术 |
|------|------|
| **后端** | Java 21 (虚拟线程) · Spring Boot 3.3 · MyBatis-Plus 3.5 |
| **代码解析** | Tree-sitter (AST) · JGit 6.10 (纯 Java Git) |
| **存储** | MySQL 8.0 · Redis 7 · Elasticsearch 8.13 · MinIO |
| **前端** | Vue 3.5 · Vite · Element Plus 2.9 · marked.js · Axios |
| **AI** | 多 LLM 适配 (OpenAI/Claude/DeepSeek/通义千问 等 9 家) · text-embedding-3-small (768 维) |
| **认证** | JWT (jjwt) · PBKDF2WithHmacSHA256 · HandlerInterceptor 无状态 |
| **部署** | Docker Compose · Nginx · 多阶段构建 · 一键脚本 |

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

```bash
docker compose ps          # 查看状态
docker compose logs -f     # 查看日志
docker compose down        # 停止服务
docker compose restart     # 重启服务
```

### 环境变量

```bash
LLM_BASE_URL=https://api.deepseek.com    # API 地址
LLM_API_KEY=sk-xxx                        # API Key
LLM_MODEL=deepseek-chat                   # 模型名称
MAIL_HOST=smtp.163.com                    # SMTP 服务器
MAIL_USERNAME=your@163.com                # 邮箱账号
MAIL_PASSWORD=your-auth-code              # 邮箱密码
MYSQL_PASSWORD=codexray123                # 数据库密码
```

---

## 项目结构

```
CodeXray/
├── src/main/java/com/codexray/
│   ├── controller/         # REST API 端点（9 个控制器）
│   ├── service/            # 业务逻辑层（12 个服务）
│   ├── agent/              # 分析流水线（4 阶段 Agent + ReAct 对话 Agent）
│   ├── rag/                # RAG 检索层（切片/向量/路由/上下文/缓存）
│   ├── llm/                # LLM 客户端（兼容 OpenAI/Claude 格式，9 家预设）
│   ├── mapper/             # MyBatis-Plus Mapper
│   ├── model/              # 9 个实体 + 16 个 DTO
│   ├── common/             # 通用工具（JWT、Result、CurrentUser）
│   └── config/             # 配置类（CORS、认证拦截器）
├── frontend/src/
│   ├── views/              # 页面（11 个路由视图）
│   ├── components/         # 组件（登录/设置/搜索面板/代码浏览器）
│   ├── api/                # API 客户端
│   ├── layout/             # 布局（侧边栏 + 用户卡片）
│   ├── router/             # Vue Router 配置
│   └── stores/             # Pinia 状态管理
├── docker-compose.yml      # 6 服务编排
├── Dockerfile              # 后端多阶段构建
├── deploy.sh               # 一键部署脚本
└── pom.xml                 # Maven 依赖（25+ 依赖项）
```

---

## 核心亮点

| 亮点 | 说明 |
|------|------|
| **四阶段 Agent 流水线** | 扫描 → 索引 → 分析 → 报告，Java 21 虚拟线程并行加速，Map-Reduce 多维度分析 |
| **RAG 检索增强** | 向量检索 + 全文检索双通道，Redis 缓存 Embedding，意图路由智能分发 |
| **ReAct 智能体** | 推理 → 行动 → 观察循环，4 种工具自主调用，ReflectionService 质量兜底 |
| **多 LLM 适配** | 一套代码兼容 9 大 AI 服务商，配置预设一键切换，连接测试即时验证 |
| **语义代码切片** | 按 class/function 边界智能分割，支持 25+ 文件格式，SHA-256 去重 |
| **一键 Docker 部署** | 6 个服务一键启动，自动建表，开箱即用 |
| **数据持久化** | MySQL + Redis + ES + MinIO 四件套，分析结果永久保存 |
| **AI Code Review** | 5 维度逐 Hunk 审查，支持 Diff 提交和已有文件审查 |
| **报告对比** | 同仓库两次分析差异对比，版本迭代质量回归一目了然 |

---

## License

[Apache License 2.0](LICENSE)

---

## 贡献

欢迎提交 [Issue](https://github.com/Fordbeing/CodeXray/issues) 和 [Pull Request](https://github.com/Fordbeing/CodeXray/pulls)！

**如果这个项目对你有帮助，点个 Star 支持一下，这是对我最大的鼓励！**

[![Star History Chart](https://api.star-history.com/svg?repos=Fordbeing/CodeXray&type=Date)](https://star-history.com/#Fordbeing/CodeXray&Date)
