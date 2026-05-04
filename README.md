<div align="center">

<img src="https://img.icons8.com/fluency/96/source-code.png" width="80" alt="CodeXray Logo"/>

# CodeXray

**开源项目的 X 光机 — 粘贴仓库链接，AI 替你读懂每一行代码**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![Docker](https://img.shields.io/badge/Docker-一键部署-blue.svg)](docker-compose.yml)
[![GitHub Stars](https://img.shields.io/github/stars/Fordbeing/CodeXray?style=social)](https://github.com/Fordbeing/CodeXray)

**25+ 编程语言 · 4 阶段 Agent 流水线 · 向量 RAG 代码问答 · AI Code Review · 跨仓库分析 · GitHub 热点追踪**

[功能特性](#-功能特性) · [效果展示](#-效果展示) · [快速开始](#-快速开始) · [架构设计](#-架构设计) · [部署指南](#-部署指南)

</div>

---

## 🤔 你有没有遇到过这些问题？

- 面对一个几千 Star 的开源项目想快速了解架构和代码质量，却不知从何入手？
- 评估技术选型时需要对比多个仓库，只能逐个手动翻阅几十个文件？
- 阅读源码时遇到疑问，搜索文档和 Stack Overflow 却找不到针对当前代码的答案？
- Code Review 依赖人工经验，遗漏的安全隐患和性能陷阱要等到线上事故才发现？
- 版本迭代后想知道代码质量是变好了还是变差了，却拿不出量化的对比数据？
- 想持续关注 GitHub 技术热点，但每天刷 Trending 太费时间？

**CodeXray 就是你需要的工具。** 它是一个 AI 驱动的开源代码智能分析平台，只需要一个 GitHub 链接，几分钟内自动完成代码扫描、向量索引、多维度 AI 分析，生成专业评审报告，并支持基于检索增强生成（RAG）的对话式代码问答。

---

## 🎬 项目演示

<img width="1905" height="896" alt="整体视频" src="https://github.com/user-attachments/assets/81889a55-c64d-45d6-b09d-ab3e5d52780a" />

<p align="center">
  <video width="100%" controls poster="demo-cover.jpg">
    <source src="" type="video/mp4">
  </video>
</p>

---

## ✨ 功能特性

### 🔍 仓库深度分析 — 一键透视项目全貌

粘贴 GitHub 仓库 URL 或上传代码压缩包，系统自动执行四阶段智能流水线：

```
提交任务 → AI 预检 → 仓库克隆 → 代码扫描 & 向量索引（虚拟线程并行）
                                    ↓
                          Map-Reduce 多维并行分析
                                    ↓
                      综合评分 + 雷达图 + 改进建议 + 最终评级
```

**8 维评分体系**，每个维度 0-100 分，直观量化项目质量：

| 维度 | 分析要点 |
|------|---------|
| 代码质量 | 命名规范、函数复杂度、代码复用、异常处理 |
| 项目结构 | 模块划分、目录层次、架构模式、分层合理性 |
| 文档完善度 | README 质量、API 文档、注释覆盖率 |
| 测试覆盖 | 测试框架、用例数量、覆盖率、CI 配置 |
| 依赖管理 | 版本锁定、CVE 漏洞、License 兼容性 |
| 安全性 | 注入风险、密钥泄露、权限校验、加密算法 |
| 性能 | N+1 查询、内存泄漏、算法复杂度、缓存策略 |
| 可维护性 | 设计模式运用、职责分离、代码风格一致性 |

报告还包含：技术栈自动识别、模块结构分析、架构依赖图、AI 代码导览、优势与不足、安全风险清单、性能建议。

### 💬 RAG 代码问答 — 像聊天一样读懂源码

不只是简单的问答，而是基于 **向量语义检索 + 全文精确匹配双通道** 的检索增强生成：

| 特性 | 说明 |
|------|------|
| 🔀 双通道检索 | kNN 向量语义搜索 + Multi-match 全文检索，精准定位相关代码片段 |
| 🧠 ReAct Agent | 推理 → 行动 → 观察循环，自主调用 4 种工具（代码搜索 / 文件阅读 / 符号查找 / 依赖分析） |
| 🛡️ 质量反思 | LLM 自动评估回答质量（1-10 分），低于 7 分自动重试，确保回答可靠 |
| 🎯 意图路由 | 自动识别代码定位 / 原理解释 / 架构概览 / 优化建议，按需切换策略 |
| ⚡ SSE 流式 | 打字机效果实时输出，Markdown 实时渲染 + 代码语法高亮 |
| 💾 会话管理 | 多轮对话持久化，支持导出 Markdown，一键切换历史会话 |

### 🌐 跨仓库问答 — 一次对话，对比多个项目

同时选中多个已分析仓库，AI 跨项目检索代码，一站式回答：

> "这几个项目中哪个的配置管理方案最好？"
>
> "对比它们的认证实现方式"
>
> "这些项目共有的安全风险有哪些？"

适合技术选型调研、团队代码规范对齐、微服务架构一致性审计。

### 🔎 AI Code Review — 5 维度逐行审查

提交 `git diff` 或直接选择已分析仓库中的文件，AI 按 Hunk 逐块审查：

- **正确性** — 逻辑错误、边界条件、空指针、并发竞争
- **安全性** — SQL 注入、XSS、敏感信息泄露、权限绕过
- **性能** — N+1 查询、内存泄漏、不合理的算法复杂度
- **可读性** — 命名规范、函数长度、注释质量、魔法数字
- **最佳实践** — 设计模式、异常处理、资源管理、API 设计

每条意见标注严重程度（🔴 错误 / 🟡 警告 / 🔵 建议），审查历史可回溯。

### 📊 报告对比 — 版本迭代质量的量化标尺

对同一仓库的两次分析结果做 AI 驱动的差异对比：

- 综合评分变化趋势
- 8 个维度得分升降
- 新增风险 vs 已修复问题
- 改进建议的变化追踪

让每一次版本发布都有据可查，再也不用凭感觉说"应该是变好了"。

### 🔥 GitHub 热点推送 — 不错过任何一个值得关注的项目

- 每日自动抓取 GitHub Trending，AI 生成 **中英文双语分析**（项目定位 / 架构解读 / 应用场景 / 学习曲线）
- 按编程语言筛选，Top 3 高亮面板
- 7 天趋势柱状图 + 180 天贡献热力图
- **邮件订阅** 每日热点日报推送（8:00 AM），支持中英文语言选择
- Redis 缓存 + 异步 AI 分析，页面秒开

### 👤 GitHub 项目概览 — 开发者画像一目了然

输入任意 GitHub 用户名，即刻生成：头像 / Bio / 粉丝 / 仓库数 / 语言分布 / 账龄分析 / Star 排行 / 原创 vs Fork 占比。支持仓库搜索排序、加载更多、收藏仓库 Tab。

### 🗂️ 代码浏览器 — 不离开平台就能看源码

分析完成后直接在浏览器中浏览仓库文件树、查看源码（语法高亮）、全文搜索代码。选中代码片段可直接引用到 RAG 对话中追问。

### ⚙️ 灵活的 AI 配置 — 不绑定任何服务商

- **9 大 AI 服务商预设**：DeepSeek / OpenAI / Claude / 通义千问 / Moonshot / 智谱 / 文心 / Groq / Ollama
- 支持自定义 API 端点，配置预设一键切换
- Embedding 模型独立配置（text-embedding-3-small / bge-large-zh 等）
- **一键连接测试**：即时验证 LLM 和 Embedding API 连通性

### 👥 用户系统 & 数据管理

- JWT + PBKDF2WithHmacSHA256 密码哈希，无状态认证
- 注册 / 登录 / 修改昵称 / 关联 GitHub 用户名 / 修改密码
- 分析历史管理（搜索 / 状态筛选 / 排序 / 分页）
- 删除分析任务时自动清理 ES 索引和 MinIO 归档文件

---

## 📸 效果展示

### 仪表盘 — 可拖拽小组件 + 统计卡片 + 7 天趋势 + 180 天热力图

<img width="1905" height="896" alt="仪表盘" src="https://github.com/user-attachments/assets/7e633603-6c0c-4f75-abe3-571108a5f6dc" />

### 仓库分析 — 8 维评分雷达图 + 模块分析 + 改进建议 + 架构依赖图

<img width="1905" height="896" alt="仓库分析" src="https://github.com/user-attachments/assets/950f6105-0a21-4cce-a49d-b7dd4da95958" />

### 架构依赖图 — 交互式 DAG 展示模块间依赖关系

<img width="1905" height="896" alt="架构依赖图" src="https://github.com/user-attachments/assets/dea3f7ee-e365-4a66-b8b2-d327a56bb166" />

### AI 代码导览 — 自动推荐项目中最重要的文件并给出导读说明

<img width="1905" height="896" alt="AI代码导览" src="https://github.com/user-attachments/assets/ca788451-9671-4f99-b5bf-8861dde99adb" />

### 代码问答 — SSE 流式输出 + Markdown 实时渲染 + 代码高亮 + 追问建议

<img width="1905" height="896" alt="代码问答" src="https://github.com/user-attachments/assets/c305dcba-1ea3-4cee-8adf-db83640e4df1" />

### 跨仓库问答 — 同时选中多个仓库进行跨项目对比问答

<img width="1905" height="896" alt="跨仓库问答" src="https://github.com/user-attachments/assets/356714fd-e271-4cf4-990f-6fa76ef3a2e7" />

### AI 代码审查 — Diff 审查 + 文件审查 + 逐 Hunk 评分 + 审查历史

<img width="1905" height="896" alt="AI代码审查" src="https://github.com/user-attachments/assets/8537603d-3802-40d6-895c-4f39e8b5a575" />

### 报告对比 — 新旧报告并排对比 + 维度变化 + 对比历史

<img width="1905" height="896" alt="报告对比" src="https://github.com/user-attachments/assets/e2f005ad-a49e-43b1-aef1-b8ec5d9848eb" />

### 热点推送 — GitHub Trending 卡片 + AI 中英文分析 + 语言分布统计

<img width="1905" height="896" alt="热点推送" src="https://github.com/user-attachments/assets/83ccab74-662e-4dfe-a5ae-8ebb3871b77e" />

### 邮件订阅 — 每日热点日报推送配置界面

<img width="849" height="812" alt="邮件推送" src="https://github.com/user-attachments/assets/d5730eab-b68e-4ac4-9574-f7a10abda694" />

### 系统设置 — 9 大 AI 服务商预设 + 一键连接测试

<img width="1905" height="896" alt="系统设置" src="https://github.com/user-attachments/assets/cb15ce0d-8b68-4f43-8ff8-2037420a3daa" />

### 代码浏览器 — 文件树 + 源码查看 + 全文搜索 + 引用到对话

<img width="1905" height="896" alt="AI代码浏览器" src="https://github.com/user-attachments/assets/a1726e54-15e7-42b2-9ee7-b22b7bcd4a29" />

---

## 🚀 快速开始

### Docker 一键部署（推荐）

```bash
git clone https://github.com/Fordbeing/CodeXray.git && cd CodeXray && bash deploy.sh
```

> 需要 [Docker](https://www.docker.com/products/docker-desktop/) + Docker Compose。脚本自动完成：环境检查 → 构建镜像 → 启动 6 个服务 → 等待就绪。

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

## 🏗️ 架构设计

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
GitHub URL → JGit 浅克隆
                ↓
    ┌───────────┴───────────┐
    │  虚拟线程并行          │
ScannerAgent          IndexerAgent
(代码切片+向量化)     (配置解析+技术栈识别)
    │                       │
    └───────────┬───────────┘
                ↓
         PlannerAgent (LLM 动态规划执行步骤)
                ↓
         AnalyzerAgent (Map-Reduce 7 类目并行分析)
         信号量限流 4 并发，避免 LLM 速率限制
                ↓
         ReporterAgent (综合报告 + 追问生成)
     并行 → MinIO 归档 (tar.gz 压缩上传)
                ↓
         完成 → 评分/雷达图/模块/建议/评级
```

### RAG 查询流程

```
用户提问 → QueryRouter (意图分类 + 关键词提取)
                ↓
        EmbeddingService (文本向量化, Redis 缓存)
                ↓
    ┌───────────┴───────────┐
    │                       │
向量检索 (kNN=6)      全文检索 (Multi-match=4)
    │                       │
    └───────────┬───────────┘
                ↓
        去重 + ContextBuilder (组装上下文, ≤15K chars)
                ↓
    ┌───────────┴───────────┐
    │                       │
LLM 生成回答          ReflectionService
(ReAct 循环, 4 轮)   (质量评分, <7 分自动重试)
    │                       │
    └───────────┬───────────┘
                ↓
         SSE 流式返回前端 (Markdown 实时渲染)
```

---

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **语言** | Java 21 (虚拟线程) · JavaScript (ES2022+) |
| **框架** | Spring Boot 3.3 · MyBatis-Plus 3.5 · Vue 3.5 · Vite 6 |
| **代码解析** | Tree-sitter (AST) · JGit 6.10 (纯 Java Git) |
| **存储** | MySQL 8.0 · Redis 7 · Elasticsearch 8.13 · MinIO |
| **AI** | 多 LLM 适配 (9 家服务商) · text-embedding-3-small (768 维) |
| **前端 UI** | Element Plus 2.9 · marked.js · highlight.js · ECharts |
| **认证** | JWT (jjwt 0.12) · PBKDF2WithHmacSHA256 · 无状态拦截器 |
| **部署** | Docker Compose · Nginx · 多阶段构建 · 一键部署脚本 |

---

## 📦 部署指南

| 服务 | 端口 | 说明 |
|------|------|------|
| `codexray-frontend` | 80 | Nginx 静态托管 + API 代理 |
| `codexray-backend` | 8081 | Spring Boot 应用 |
| `codexray-mysql` | 3306 | MySQL 8.0（自动建表） |
| `codexray-redis` | 6379 | Redis 7（AOF 持久化） |
| `codexray-es` | 9200 | Elasticsearch 8.13（向量检索） |
| `codexray-minio` | 9000 | MinIO 对象存储（分析归档） |

```bash
docker compose ps          # 查看服务状态
docker compose logs -f     # 查看实时日志
docker compose down        # 停止所有服务
docker compose restart     # 重启服务
```

### 关键环境变量

```bash
LLM_BASE_URL=https://api.deepseek.com    # AI API 地址
LLM_API_KEY=sk-xxx                        # API Key
LLM_MODEL=deepseek-chat                   # 模型名称
EMBEDDING_BASE_URL=https://api.openai.com # Embedding 服务地址
EMBEDDING_API_KEY=sk-xxx                  # Embedding API Key
MAIL_HOST=smtp.163.com                    # 邮件 SMTP 服务器
MAIL_USERNAME=your@163.com                # 邮箱账号
MAIL_PASSWORD=your-auth-code              # 邮箱授权码
MYSQL_PASSWORD=codexray123                # 数据库密码
```

---

## 📁 项目结构

```
CodeXray/
├── src/main/java/com/codexray/
│   ├── controller/         # REST API 端点（10 个控制器）
│   ├── service/            # 业务逻辑层（12+ 个服务）
│   ├── agent/              # 分析流水线（4 阶段 Agent + ReAct 对话 Agent）
│   ├── rag/                # RAG 检索层（切片/向量/路由/上下文/缓存）
│   ├── llm/                # LLM 客户端（兼容 OpenAI 格式，9 家预设）
│   ├── mapper/             # MyBatis-Plus 数据访问层
│   ├── model/entity/       # 数据库实体
│   ├── model/dto/          # 请求/响应传输对象
│   ├── common/             # 通用工具（JWT、Result、CurrentUser）
│   └── config/             # Spring 配置（CORS、认证拦截器）
├── frontend/src/
│   ├── views/              # 11 个页面视图
│   ├── components/         # 公共组件（AuthDialog、ProfileDialog 等）
│   ├── api/                # API 请求层
│   ├── layout/             # 布局组件（侧边栏 + 用户卡片）
│   ├── router/             # Vue Router 路由配置
│   ├── stores/             # Pinia 状态管理
│   └── utils/              # 工具函数
├── docker-compose.yml      # 6 服务编排
├── Dockerfile              # 后端多阶段构建
├── deploy.sh               # 一键部署脚本
└── pom.xml                 # Maven 依赖管理
```

---

## 🏆 核心亮点

| 亮点 | 说明 |
|------|------|
| **4 阶段 Agent 流水线** | 扫描 → 索引 → 分析 → 报告，Java 21 虚拟线程并行加速，Map-Reduce 多维并发分析 |
| **双通道 RAG 检索** | 向量语义检索 + 全文精确匹配双通道，Redis 缓存 Embedding，意图路由智能分发 |
| **ReAct 智能体** | 思考 → 行动 → 观察循环，4 种工具自主调用，ReflectionService 质量兜底 |
| **实时 Markdown 流式** | SSE 打字机效果，流式过程中实时 Markdown 渲染 + 代码语法高亮，无闪烁 |
| **多 LLM 自由切换** | 一套代码兼容 9 大 AI 服务商，配置预设一键切换，不绑定任何厂商 |
| **语义代码切片** | 按 class/function 边界智能分割，支持 25+ 文件格式，SHA-256 去重 |
| **Docker 一键部署** | 6 个服务一键启动，自动建表，开箱即用 |
| **AI Code Review** | 5 维度逐 Hunk 审查，支持 Diff 提交和已有文件审查，严重程度标注 |
| **报告对比** | 同仓库两次分析差异对比，版本迭代质量变化一目了然 |
| **跨仓库问答** | 同时检索多个仓库的代码，一站式跨项目对比分析 |
| **报告分享** | 生成分享链接，支持密码保护和过期时间，方便团队协作 |
| **数据永久化** | MySQL + Redis + ES + MinIO 四件套，分析结果和会话历史永久保存 |

---

## 🤝 贡献

欢迎提交 [Issue](https://github.com/Fordbeing/CodeXray/issues) 和 [Pull Request](https://github.com/Fordbeing/CodeXray/pulls)！

**如果这个项目对你有帮助，请给一个 Star ⭐，这是对我最大的鼓励！**

[![Star History Chart](https://api.star-history.com/svg?repos=Fordbeing/CodeXray&type=Date)](https://star-history.com/#Fordbeing/CodeXray&Date)

---

## 📄 License

[Apache License 2.0](LICENSE) © 2024-present CodeXray Contributors
