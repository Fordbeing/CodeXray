# Contributing to CodeXray

感谢你对 CodeXray 的关注！欢迎提交 Issue 和 Pull Request。

## 如何贡献

1. **Fork** 本仓库
2. 创建特性分支 (`git checkout -b feature/xxx`)
3. 提交更改 (`git commit -m 'feat: add xxx'`)
4. 推送到分支 (`git push origin feature/xxx`)
5. 提交 **Pull Request**

## 开发环境

### 后端

- Java 21+
- Maven 3.8+
- MySQL 8.0、Redis 7、Elasticsearch 8.13、MinIO

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端

- Node.js 18+

```bash
cd frontend && npm install && npm run dev
```

## Commit 规范

| 前缀 | 说明 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | 修复 bug |
| `docs:` | 文档变更 |
| `style:` | 代码格式（不影响逻辑） |
| `refactor:` | 重构 |
| `perf:` | 性能优化 |
| `test:` | 测试相关 |

## 报告 Issue

请使用 [Issue 模板](https://github.com/Fordbeing/CodeXray/issues/new/choose) 提交 bug 报告或功能建议。

## License

贡献的代码将遵循 [Apache License 2.0](LICENSE)。
