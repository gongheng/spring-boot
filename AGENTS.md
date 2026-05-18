# Spring Boot 源码仓库 — Agent 备注

这是 **Spring Boot 框架源码仓库**（spring-projects/spring-boot），不是 Spring Boot 应用项目。大型 Gradle 多模块项目（~200+ 模块）。

## 构建与验证

```bash
# 完整构建（编译 + 测试 + checkstyle）
./gradlew build

# 发布到本地 Maven 缓存（跳过测试）
./gradlew publishToMavenLocal

# 仅验证（测试 + checkstyle，不打包）
./gradlew check

# 格式化代码（Spring JavaFormat）
./gradlew format

# 格式化 buildSrc 代码
./gradlew -p buildSrc format

# 仅 checkstyle
./gradlew checkstyleMain checkstyleTest

# 构建单个模块
./gradlew :module:spring-boot-actuator:build

# 运行单个测试类
./gradlew :core:spring-boot:test --tests "org.springframework.boot.SomeTest"

# 运行模块的集成测试（需要 intTest plugin）
./gradlew :module:spring-boot-devtools:intTest
```

**常见陷阱：** 运行测试前需取消设置 `SPRING_PROFILES_ACTIVE`，否则会影响测试结果：
```bash
unset SPRING_PROFILES_ACTIVE && ./gradlew check
```

## JDK 要求

- **主 JDK：25**（见 `.sdkmanrc`：`java=25.0.2-librca`）
- CI 还通过 toolchain 测试 JDK 17、21 和 26
- buildSrc 使用 source/target compatibility 17 编译

## 模块结构

| 目录 | 用途 |
|------|------|
| `core/` | 框架核心：`spring-boot`、`spring-boot-autoconfigure`、`spring-boot-test`、`spring-boot-testcontainers` |
| `module/` | 自动配置模块（actuator、webmvc、jdbc、security 等） |
| `starter/` | Starter 依赖 POM（`spring-boot-starter-*`） |
| `loader/` | 可执行 jar 加载基础设施 |
| `build-plugin/` | Gradle 插件、Maven 插件、Ant lib |
| `cli/` | Spring Boot CLI |
| `platform/` | BOM（`spring-boot-dependencies`） |
| `configuration-metadata/` | 配置属性注解处理器 |
| `smoke-test/` | 端到端冒烟测试应用（独立 Gradle 模块） |
| `integration-test/` | 集成测试（loader、actuator、server 等） |
| `system-test/` | 部署/镜像系统测试 |
| `documentation/` | 参考文档（Antora） |
| `test-support/` | 共享测试工具 |

## 代码风格与约定

- **Spring JavaFormat** 通过 `io.spring.javaformat` 插件和 Checkstyle 强制执行
- **缩进：Tab**（4 宽度），续行缩进 8 — 见 `.editorconfig`
- **License 头**：所有 `.java` 文件必须有 Apache 2.0 头（从现有文件复制）
- **Javadoc**：每个新类至少需要 `@author` 标签和描述段落
- **Commit 消息**：遵循 [tpope 格式](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)
- **DCO**：所有 commit 必须包含 `Signed-off-by:` 尾部声明

### Jackson 导入规则

`buildSrc` 中的 Checkstyle **禁止 `com.fasterxml.jackson` 导入**（`com.fasterxml.jackson.annotation` 除外）。项目同时使用 Jackson 2.x（`com.fasterxml.jackson`）和 Jackson 3.x（`tools.jackson`）。新代码应使用 `tools.jackson`（v3）。

## Gradle 构建架构

所有子项目自动应用 `org.springframework.boot.conventions` 插件（见 `ConventionsPlugin.java`），该插件会引入：
- NoHttp 检查
- Spring JavaFormat + Checkstyle
- Maven 发布约定
- Kotlin 约定
- Toolchain 支持

自定义 Gradle 插件位于 `buildSrc/`，在 `buildSrc/build.gradle` 的 `gradlePlugin.plugins` 中注册。关键插件 ID：

| Plugin ID | 用途 |
|-----------|------|
| `org.springframework.boot.conventions` | 应用于所有子项目 |
| `org.springframework.boot.auto-configuration` | 自动配置模块设置 |
| `org.springframework.boot.configuration-properties` | 配置属性元数据 |
| `org.springframework.boot.deployed` | Maven 发布设置 |
| `org.springframework.boot.optional-dependencies` | `optional()` 配置 |
| `org.springframework.boot.starter` | Starter POM 生成 |
| `org.springframework.boot.integration-test` | 添加 `intTest` source set |
| `org.springframework.boot.docker-test` | 添加 `dockerTest` source set |
| `org.springframework.boot.system-test` | 添加 `systemTest` source set |

## 测试类型

- **单元测试**：标准 `test` source set（JUnit Jupiter 6）
- **集成测试**：`intTest` source set — 需要 `org.springframework.boot.integration-test` 插件
- **Docker 测试**：`dockerTest` source set — 需要 Docker，使用 Testcontainers
- **系统测试**：`systemTest` source set — 部署/镜像验证
- **冒烟测试**：`smoke-test/` 下的独立完整应用模块

## 关键版本（来自 `gradle.properties`）

- Spring Framework：7.0.7
- JUnit Jupiter：6.0.3
- Kotlin：2.3.21
- Mockito：5.23.0
- AssertJ：3.27.7
- 当前项目版本：4.1.0-SNAPSHOT

## CI

- PR 构建：`.github/workflows/build-pull-request.yml` — 运行 `./gradlew build`
- 主分支：`.github/workflows/build-and-deploy-snapshot.yml` — 构建 + 发布到 repo.spring.io
- CI 矩阵：Linux + Windows × JDK 17、21、25、26

## 参考资料

- [CONTRIBUTING.adoc](CONTRIBUTING.adoc) — 贡献指南
- [Wiki: Working with the Code](https://github.com/spring-projects/spring-boot/wiki/Working-with-the-Code)
- [Spring JavaFormat](https://github.com/spring-io/spring-javaformat/)
