# CLAUDE.md

## Project Overview

**CodeXray** — 输入 GitHub 仓库链接，基于 LLM + 代码解析生成专业分析报告，支持对话式代码问答与每日热点推送。

Licensed under Apache 2.0.

## Tech Stack

- **Language**: Java 17+ / Spring Boot 3.x
- **ORM**: MyBatis-Plus
- **Database**: MySQL 8.0 (business data), Redis 7.x (cache/session/rate-limit)
- **Messaging**: RocketMQ (async analysis tasks, push notifications)
- **Scheduling**: XXL-Job or Spring @Scheduled
- **Git Ops**: JGit (pure Java Git implementation)
- **Code Parsing**: Tree-sitter via JNI (multi-language AST)
- **Vector DB**: Milvus or Elasticsearch 8.x (RAG code embedding search)
- **LLM**: Unified client wrapping OpenAI/Claude/DeepSeek/通义千问
- **Embedding**: text-embedding-3-small / bge-large-zh
- **Email**: Spring Boot Starter Mail
- **HTTP**: OkHttp / WebClient
- **Container**: Docker (isolate clone environments)

## Core Features

1. **仓库深度分析** — Clone → AST parse → LLM analysis → report with Mermaid architecture diagram, score card
2. **对话式代码问答 (RAG)** — Code slicing → embedding → vector search → LLM answer with file/line references
3. **每日热点推送** — GitHub Trending scraping → aggregation → email push

## Project Structure

```
src/main/java/com/codexray/
├── CodeXrayApplication.java           — 启动类
├── controller/                        — REST 端点
├── service/                           — 业务编排
├── llm/                               — LLM 客户端接口 + 实现
├── mapper/                            — MyBatis-Plus Mapper
├── model/entity/                      — DB 实体
├── model/dto/                         — 请求/响应 DTO
├── common/                            — 通用封装（Result）
└── config/                            — 配置类
```

## Conventions

- License: Apache 2.0
- Java 17+, Spring Boot 3.x
- Dev profile uses H2 (in-memory), prod uses MySQL
- REST API: `POST /api/analysis/analyze` → returns taskId, `GET /api/analysis/{taskId}` → returns result
