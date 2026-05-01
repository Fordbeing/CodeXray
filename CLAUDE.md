# CLAUDE.md

## Project Overview

**CodeXray** — 输入 GitHub 仓库链接，基于 LLM + 代码解析生成专业分析报告，支持对话式代码问答与每日热点推送。

Licensed under Apache 2.0.

## Tech Stack

### Backend
- **Language**: Java 21+ / Spring Boot 3.x (虚拟线程并行分析流水线)
- **ORM**: MyBatis-Plus
- **Database**: MySQL 8.0 (business data), Redis 7.x (cache/session/rate-limit)
- **Auth**: JWT (jjwt) + PBKDF2WithHmacSHA256 密码哈希, HandlerInterceptor 无状态认证
- **Messaging**: RocketMQ (async analysis tasks, push notifications)
- **Scheduling**: XXL-Job or Spring @Scheduled
- **Git Ops**: JGit (pure Java Git implementation)
- **Code Parsing**: Tree-sitter via JNI (multi-language AST)
- **Vector DB**: Milvus or Elasticsearch 8.x (RAG code embedding search)
- **LLM**: Unified client wrapping OpenAI/Claude/DeepSeek/通义千问
- **Embedding**: text-embedding-3-small / bge-large-zh
- **Email**: Spring Boot Starter Mail
- **HTTP**: OkHttp / WebClient / GitHub REST API v3 代理
- **Container**: Docker (isolate clone environments)

### Frontend
- **Framework**: Vue 3.5 + Vite
- **UI Library**: Element Plus 2.9
- **Router**: Vue Router 4.5
- **Markdown**: marked.js 渲染对话内容 (GFM)
- **HTTP**: Axios 1.7 (自动附带 JWT Bearer token), SSE 流式输出 (fetch + ReadableStream)
- **Auth**: JWT token 存储于 localStorage, 登录/注册弹窗组件
- **External API**: GitHub REST API v3 (通过后端代理或直接调用)

## Core Features

1. **仓库深度分析** — Clone → AST parse → LLM analysis → report, 虚拟线程并行 Scanning+Indexing, 预览结果缓存
2. **对话式代码问答 (RAG)** — Code slicing → embedding → vector search → LLM answer, SSE 流式输出, marked.js Markdown 渲染
3. **每日热点推送** — GitHub Trending scraping → aggregation → email push + 语言分布统计 + Top 3 高亮面板 + 中英文 AI 分析
4. **GitHub 项目概览** — 独立页面展示用户头像/bio/粉丝统计/语言分布/账龄，支持仓库搜索排序/加载更多/收藏仓库 Tab
5. **用户系统** — 注册/登录（JWT + PBKDF2 哈希），个人设置（修改昵称、关联 GitHub 用户名、修改密码），侧边栏用户卡片
6. **分析历史管理** — 搜索/状态筛选/排序/分页，统计卡片，快捷跳转报告和问答，删除时清理 ES + MinIO 数据
7. **邮件订阅管理** — 订阅/退订热点日报，支持中英文语言选择

## Project Structure

```
src/main/java/com/codexray/
├── CodeXrayApplication.java           — 启动类
├── controller/                        — REST 端点 (analysis, chat, trending, email, auth, github)
├── service/                           — 业务编排
├── agent/                             — 分析流水线 (scanner, indexer, analyzer, reporter)
├── rag/                               — RAG 层 (chunker, embedding, vector store, query router)
├── llm/                               — LLM 客户端接口 + 实现
├── mapper/                            — MyBatis-Plus Mapper
├── model/entity/                      — DB 实体 (含 User)
├── model/dto/                         — 请求/响应 DTO (含 LoginRequest, RegisterRequest, ChangePasswordRequest, UserResponse)
├── common/                            — 通用封装 (Result, JwtUtil, CurrentUser)
└── config/                            — 配置类 (WebConfig, AuthConfig, AuthInterceptor)

frontend/src/
├── api/                               — API 客户端 (analysis, chat, trending, github, auth)
├── components/                        — 公共组件 (AuthDialog, ProfileDialog)
├── views/                             — 页面组件 (Dashboard, Analyze, Chat, Trending, History, GitHub)
├── layout/                            — 布局组件 (AppLayout, AppSidebar - 含用户卡片)
├── router/                            — Vue Router 配置
├── styles/                            — 全局样式 (variables.css)
└── utils/                             — 工具函数 (status.js)
```

## Conventions

- License: Apache 2.0
- Java 17+, Spring Boot 3.x
- Dev profile uses H2 (in-memory), prod uses MySQL
- REST API: `POST /api/analysis/analyze` → returns taskId, `GET /api/analysis/{taskId}` → returns result
