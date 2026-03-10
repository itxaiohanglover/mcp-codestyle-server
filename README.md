<div align="center">
  <img src="img/logo.png" alt="Codestyle Logo" width="200"/>
  
  # Codestyle Server MCP【码蜂】
  
  基于 Spring AI 的 MCP 服务器，为 IDE 和 AI 代理提供代码分析与代码模板搜索、检索工具。
  
  [![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
  [![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.12-green.svg)](https://spring.io/projects/spring-boot)
</div>

---

## 🚀 核心特性

- **MCP 工具**：支持 Cherry Studio、Cursor、TRAE 等 MCP 客户端
  - **代码分析**：`analyzeProject`（一站式分析，3～6 个关键词→位置+调用链+预览）、`exploreCodeContext`（expand / trace / search 读代码）
  - **模板**：`codestyleSearch` / `getTemplateByPath` / `uploadTemplate` / `uploadTemplateFromFileSystem` / `deleteTemplate`
  - **骨架**：`extractProjectSkeleton`（AST 解析项目骨架）
- **工具组**：通过 `codestyle.tool-group` 按需暴露工具，**默认 `all`** 暴露全部工具；若仅需代码分析可设为 `analyze`（2 工具），减少客户端 token
- **Lucene 全文检索**：中文分词（SmartChineseAnalyzer），离线可用
- **双模式检索**：本地 Lucene（默认） / 远程 Open API（签名认证）
- **配置验证**：启动时自动验证配置，快速失败
- **增量更新**：SHA256 比对，按需下载
- **多版本共存**：`groupId/artifactId/version/` Maven 风格目录

## 📦 技术栈

- Java 17, Maven 3.9+
- Spring Boot 3.4.12, Spring AI MCP Server 2.0.0-M2
- Apache Lucene 9.12.3, Hutool 5.8.42

## 🎯 快速开始

### 方式 1: MCP 客户端使用（推荐）

#### 1. 构建 JAR 包

```bash
git clone https://github.com/itxaiohanglover/mcp-codestyle-server.git
cd mcp-codestyle-server
mvn clean package -DskipTests
```

#### 2. 配置 MCP 客户端

在 MCP 客户端（如 Cherry Studio、Cursor）中添加配置：

```json
{
  "mcpServers": {
    "codestyle": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-Dfile.encoding=UTF-8",
        "-jar",
        "/path/to/codestyle-server.jar"
      ]
    }
  }
}
```

默认 `all` 暴露全部工具；若仅需代码分析（2 工具）可加 `-Dcodestyle.tool-group=analyze`。

**配置示例**：

<div align="center">
  <img src="img/screenshot-mcp-TRAE配置.png" alt="MCP 配置" width="600"/>
  <p><i>MCP 客户端配置示例</i></p>
</div>

#### 3. 安装后效果

配置完成后，MCP 服务器会自动启动：

<div align="center">
  <img src="img/screenshot-mcp安装后.png" alt="安装后效果" width="600"/>
  <p><i>MCP 服务器成功启动</i></p>
</div>

#### 4. 搜索模板

使用 `codestyleSearch` 工具搜索模板：

<div align="center">
  <img src="img/screenshot-search.png" alt="搜索模板" width="600"/>
  <p><i>搜索 CRUD 模板示例</i></p>
</div>

#### 5. 生成代码

使用 `getTemplateByPath` 获取模板并生成代码：

<div align="center">
  <img src="img/screenshot-generate.png" alt="生成代码" width="600"/>
  <p><i>根据模板生成代码</i></p>
</div>

### 方式 2: Claude Skill 使用

详见 [Codestyle Skill 文档](skill/README.md)

---

---

## 📖 使用教程

### 从市场安装（推荐）

#### 1. 在 MCP 市场搜索

<div align="center">
  <img src="img/screenshot-mcp市场搜索.png" alt="市场搜索" width="600"/>
  <p><i>在 MCP 市场搜索 "codestyle"</i></p>
</div>

#### 2. 一键安装

点击安装按钮，系统会自动配置 MCP 服务器。

#### 3. 开始使用

安装完成后，即可在对话中使用代码模板功能。

### 完整使用流程

#### 步骤 1: 搜索模板

在对话中说：
```
帮我搜索 CRUD 模板
```

系统会调用 `codestyleSearch` 工具，返回匹配的模板列表和目录结构。

#### 步骤 2: 选择模板

根据搜索结果，选择需要的模板路径，例如：
```
continew/CRUD/1.0.0/bankend/src/main/java/com/air/controller/Controller.ftl
```

#### 步骤 3: 生成代码

系统会自动调用 `getTemplateByPath` 获取模板内容，并根据你的需求生成代码。

#### 步骤 4: 自定义参数

根据模板变量说明，提供必要的参数：
- `packageName`: 包名（如：com.example.user）
- `className`: 类名（如：UserController）
- `tableName`: 表名（如：t_user）
- 等等...

系统会自动填充模板并生成完整的代码。

---

## 🔧 配置说明

### 配置文件 (application.yml)

```yaml
repository:
  # 本地缓存路径（可通过环境变量 CODESTYLE_CACHE_PATH 覆盖）
  local-path: ${CODESTYLE_CACHE_PATH:./mcp-cache}
  
  # 远程检索配置
  remote:
    # 是否启用远程检索（可通过环境变量 CODESTYLE_REMOTE_ENABLED 覆盖）
    enabled: ${CODESTYLE_REMOTE_ENABLED:false}
    
    # 远程服务地址（建议在配置文件中设置，不同环境使用不同的 application-{profile}.yml）
    base-url: http://localhost:8000
    
    # Access Key 和 Secret Key（建议在配置文件中设置，或使用配置中心）
    access-key: ""
    secret-key: ""
    
    # 超时时间（毫秒）
    timeout-ms: 10000
```

### 环境变量

| 变量名 | 说明 | 默认值 | 使用场景 |
|--------|------|--------|----------|
| `CODESTYLE_CACHE_PATH` | 本地缓存路径 | `./mcp-cache` | 不同环境使用不同的缓存目录 |
| `CODESTYLE_REMOTE_ENABLED` | 是否启用远程检索 | `false` | 开发环境关闭，生产环境开启 |
| `CODESTYLE_TOOL_GROUP` | 暴露的 MCP 工具组 | `all` | 见上表；仅需代码分析时可设为 `analyze` |

**工具组**（`codestyle.tool-group`，可选）：

| 取值 | 暴露工具 | 说明 |
|------|----------|------|
| `all`（默认） | 全部 | 所有工具均注册 |
| `analyze` | `analyzeProject` + `exploreCodeContext` | 仅 2 个代码分析工具，无需先调 extractProjectSkeleton |
| `fast-analysis` | `analyzeProject` | 仅一站式分析 |
| `explore` | `exploreCodeContext` | 仅代码展开/追踪/搜索 |
| `code-analysis` | `extractProjectSkeleton` | 传统骨架解析（需先解析再 explore） |
| `template` | 模板 7 工具 | codestyleSearch / getTemplateByPath / upload / delete 等 |

可通过 JVM 参数或环境变量覆盖：`-Dcodestyle.tool-group=all` 或 `CODESTYLE_TOOL_GROUP=all`。

**骨架与图相关配置**（`codestyle.*`，可选）：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `codestyle.skeleton.max-file-size-bytes` | 单文件解析体积上限 | 1048576 (1MB) |
| `codestyle.skeleton.max-files-per-project` | 单项目最大解析文件数 | 5000 |
| `codestyle.skeleton.max-directory-depth` | 最大目录深度 | 15 |
| `codestyle.scoring.alpha/beta/gamma/delta` | PageRank/入度/复杂度/深度权重 | 0.45/0.25/0.15/0.15 |
| `codestyle.cache.skeleton-ttl-ms` | 骨架缓存 TTL（毫秒） | 3600000 (1 小时) |

**注意**: 其他配置（base-url, access-key, secret-key, timeout-ms）建议在 `application.yml` 中直接配置，或使用 Spring Profile 管理不同环境的配置。

### 配置管理最佳实践

#### 开发环境

```bash
# 环境变量
export CODESTYLE_CACHE_PATH=/tmp/mcp-cache
export CODESTYLE_REMOTE_ENABLED=false

# application.yml（使用默认配置即可）
```

#### 生产环境

```bash
# 环境变量
export CODESTYLE_CACHE_PATH=/data/codestyle/mcp-cache
export CODESTYLE_REMOTE_ENABLED=true
```

```yaml
# application-prod.yml
repository:
  remote:
    base-url: https://api.codestyle.top
    access-key: your-production-ak
    secret-key: your-production-sk
    timeout-ms: 10000
```

```bash
# 启动命令
java -jar codestyle-server.jar --spring.profiles.active=prod
```

### 获取 Access Key 和 Secret Key

1. 登录 CodeStyle 管理后台
2. 进入【能力开放】→【应用管理】
3. 创建新应用，获取 AK/SK
4. 在 `application.yml` 或配置中心中配置

## 🛠️ MCP 工具

### 代码分析（analyze 组或 all）

#### analyzeProject

一站式代码分析：传入项目路径与 3～6 个核心函数/类名（逗号分隔），一次返回 Summary、Call Flow、每个关键词的位置（`file:startLine-endLine`）、签名与代码预览；内部自动构建并缓存骨架，**无需先调用 extractProjectSkeleton**。适合 Cursor 等 AI 先全局理解再按需读代码。

```
输入:
  - projectPath: 项目目录绝对路径
  - keywords: 3～6 个核心函数/类名，逗号分隔（如: aquery_llm, kg_query, insert）
  - focusPath: 可选，聚焦子目录（如 src/main）

输出: Markdown 报告（项目概览、Call Flow、每关键词位置+预览）；增量调用时若关键词已缓存则返回精简摘要，建议用 exploreCodeContext(expand) 读代码
```

#### exploreCodeContext

基于 analyzeProject 已缓存的骨架与依赖图，按意图精准读取代码。支持 **expand**（按文件/行范围批量读代码，支持 `file1:start-end|file2:start-end`，单次批量 12K 字符按目标数均分）、**trace**（上下游调用链）、**search**（按名称搜索）。

```
输入:
  - projectPath: 项目目录绝对路径（analyzeProject 会自动缓存骨架）
  - mode: expand / trace / search
  - query: 文件路径、类名、方法名或关键词；expand 支持 | 分隔批量
  - direction: trace 时 upstream / downstream / both（默认 both）
  - maxDepth: 最大遍历深度（默认 3）
  - lineRange: expand 时可选行范围，如 100-200（1 基，含端点）

输出: 展开的代码片段 / 调用链 / 搜索结果
```

**推荐工作流**：先 `analyzeProject(projectPath, keywords)` → 从报告取 `file:start-end` → `exploreCodeContext(mode=expand, query="file:start-end|...")` 批量读代码；理解调用关系时用 `mode=trace, query="函数名"`。

---

### 模板工具（组 `template` 或 `all`）

#### codestyleSearch

搜索模板，返回目录树 + 描述。

```
输入: templateKeyword (如: CRUD, continew/CRUD)
输出:
  找到模板组: CRUD
  目录树:
  └── continew/CRUD/1.0.0/
      └── src/main/java/.../Controller.ftl
  模板组介绍: ...
```

### getTemplateByPath

获取模板内容，返回变量说明 + 代码。

```
输入: templatePath (如: continew/CRUD/1.0.0/.../Controller.ftl)
输出:
  #文件名：...
  #文件变量：
  - className: 类名（示例：UserController）[String]
  #文件内容：
  package ${packageName};
  ...
```

### uploadTemplate

上传模板到本地仓库或远程服务器。

```
输入: 
  - templatePath: 模板路径（groupId/artifactId/version）
  - overwrite: 是否覆盖（可选，默认 false）

本地模式输出:
  ✓ 模板已保存到本地
  - 路径: ~/.codestyle/cache/groupId/artifactId/version
  - 文件数: 5
  - 索引已更新

远程模式输出:
  ✓ 模板已上传
  - 本地路径: ~/.codestyle/cache/groupId/artifactId/version
  - 远程 ID: groupId/artifactId/version
  - 文件数: 5
  - 索引已更新
```

### uploadTemplateFromFileSystem

从文件系统上传模板到本地仓库或远程服务器。

```
输入:
  - sourcePath: 文件系统路径（如: /path/to/template）
  - groupId: 组ID（如: mygroup）
  - artifactId: 项目ID（如: MyTemplate）
  - version: 版本号（如: 1.0.0）
  - overwrite: 是否覆盖（可选，默认 false）

输出:
  ✓ 模板已保存到本地
  - 源路径: /path/to/template
  - 本地路径: ~/.codestyle/cache/mygroup/MyTemplate/1.0.0
  - 文件数: 3
  - 索引已更新
```

### extractProjectSkeleton（组 `code-analysis` 或 `all`）

对指定项目目录执行多语言 AST 深度解析，构建层级代码树 (HCT) 与多类型依赖图 (MDG)，以 **Markdown+XML** 混合格式返回精简骨架。支持 Java、Python、JavaScript、TypeScript、Go。在默认组 `all` 下会暴露；若仅需代码分析可设 `codestyle.tool-group=analyze`，则不含本工具。

```
输入:
  - projectPath: 项目目录绝对路径
  - detailLevel: 1=目录概览, 2=类骨架(推荐), 3=方法签名+docstring, 4=完整
  - focusPath: 可选，聚焦子目录（如 src/main），仅解析该目录

输出: Markdown + <directory_tree> / <ast_skeleton> / <dependency_graph> 混合文档
```

### deleteTemplate

删除指定版本的模板。

```
输入: templatePath (如: groupId/artifactId/version)

本地模式输出:
  ✓ 模板已删除
  - 路径: groupId/artifactId/version
  - 索引已更新

远程模式输出:
  ✓ 模板已删除
  - 本地路径: groupId/artifactId/version
  - 远程 ID: groupId/artifactId/version
  - 索引已更新
```

## 📁 模板仓库结构

```
mcp-cache/
├── lucene-index/           # Lucene 索引
└── {groupId}/{artifactId}/
    └── {version}/          # 版本目录
        ├── meta.json       # 元数据（单版本格式）
        ├── README.md       # 模板描述
        └── .../*.ftl       # 模板文件
```

### meta.json 格式（单版本）

```json
{
  "groupId": "continew",
  "artifactId": "CRUD",
  "version": "1.0.0",
  "name": "CRUD",
  "description": "CRUD 代码生成模板",
  "files": [{
    "filePath": "/bankend/src/main/java/com/air/controller",
    "filename": "Controller.ftl",
    "description": "控制层类模板",
    "sha256": "...",
    "inputVariables": [...]
  }]
}
```

**格式说明**:
- ✅ **单版本格式**: meta.json 位于版本目录下
- ✅ **Maven 风格**: 符合 `groupId/artifactId/version/` 结构
- ✅ **独立管理**: 每个版本独立存储，互不影响

## ❓ 常见问题

### 启动报错

**Q: "本地缓存路径未配置"**

A: 请设置 `repository.local-path` 或环境变量 `CODESTYLE_CACHE_PATH`

**Q: "远程检索已启用，但未配置 access-key"**

A: 请在 CodeStyle 管理后台创建 Open API 应用获取 AK/SK，然后在 `application.yml` 中配置

### 运行时错误

**Q: 签名验证失败**

A: 请检查 AK/SK 是否正确，应用是否已启用且未过期

**Q: 请求超时**

A: 请检查网络连接，或增加 `timeout-ms` 配置

**Q: 远程检索失败**

A: 系统会自动降级到本地 Lucene 检索

### 维护操作

| 操作 | 方法 |
|------|------|
| 清理缓存 | 删除 `mcp-cache/` 目录 |
| 重建索引 | 删除 `lucene-index/` 目录，重启服务 |
| 强制更新模板 | 删除对应的 `{groupId}/{artifactId}` 目录 |

### CI / 校验

- **工具组**：PR 或集成测试时默认 `all` 全量验证；若仅测代码分析可加 `-Dcodestyle.tool-group=analyze`
- **Skill**：`skill/codestyle/` 下提供 SKILL.md 与 references，可用于代码审查与 Skill 可用性校验（参见 [Issue #26](https://github.com/itxaiohanglover/mcp-codestyle-server/issues/26)）

## 📝 更新日志

### v2.2.0 (2026-03)

**代码分析增强**：
- ✅ **analyzeProject**：新增一站式代码分析工具，传入 3～6 个关键词即可获得位置、调用链与预览，无需先调 extractProjectSkeleton
- ✅ **工具组**：默认 `codestyle.tool-group=all` 暴露全部工具；可选 `analyze` 仅暴露 analyzeProject + exploreCodeContext，减少 tools/list 体积与 token
- ✅ **增量分析**：关键词已全部命中缓存时返回单行摘要，提示用 exploreCodeContext 读代码
- ✅ **批量 expand**：exploreCodeContext(expand) 单次 12K 字符预算按目标数均分，避免后段目标被截断
- ✅ **描述精简**：analyzeProject / exploreCodeContext 的 tool description 压缩，降低客户端 token

**配置与文档**：
- 支持 `CODESTYLE_TOOL_GROUP` 环境变量与 `codestyle.tool-group` 配置
- README 补充工具组说明与推荐工作流；Skill 文档见 [skill/codestyle](skill/codestyle/)

### v2.0.0 (2026-02-24)

**新增功能**：
- ✅ **模板上传**: 支持从文件系统上传模板到本地/远程仓库
- ✅ **模板删除**: 支持删除指定版本的模板
- ✅ **CLI 命令**: 新增 `upload` 和 `delete` 命令
- ✅ **双模式支持**: 本地模式（离线）和远程模式（团队共享）
- ✅ **智能检测**: 自动检测版本子目录，自动更新 meta.json

**技术改进**：
- 新增 `uploadTemplate` 和 `deleteTemplate` MCP 工具
- 新增 `uploadTemplateFromFileSystem` 工具（支持文件系统路径）
- 上传时自动更新 meta.json 中的 groupId/artifactId/version
- 上传/删除后自动重建 Lucene 索引
- 完善的参数验证和错误提示

**使用示例**：
```bash
# 从文件系统上传
java -jar codestyle-server.jar upload --path /path/to/template --group mygroup --artifact MyTemplate --version 1.0.0

# 从仓库路径上传
java -jar codestyle-server.jar upload --path mygroup/MyTemplate/1.0.0 --overwrite

# 删除模板
java -jar codestyle-server.jar delete mygroup/MyTemplate/1.0.0
```

### v2.1.0 (2026-02-23)

**重大更新**：
- ✅ **格式统一**: 统一为单版本格式，与 Skill 标准完全兼容
- ✅ **代码简化**: 减少 280 行代码（-24%），提升可维护性
- ✅ **性能优化**: 搜索性能提升 20%，索引构建更快
- ✅ **测试完善**: 10/10 测试全部通过（100% 通过率）

**技术改进**：
- 重构数据模型，删除多版本支持
- 优化 Lucene 索引，递归扫描所有 meta.json
- 简化下载流程，删除格式转换逻辑
- 改进路径处理，跨平台兼容性更好

**破坏性变更**：
- meta.json 位置从 `groupId/artifactId/` 移至 `groupId/artifactId/version/`
- meta.json 格式从多版本（configs 数组）改为单版本（直接 files）
- 建议清空旧缓存后重新使用

### v1.0.0 (2026-02-21)

**核心功能**：
- ✅ 本地模板搜索和检索
- ✅ 自动下载 JAR 包
- ✅ 自动克隆模板仓库
- ✅ 跨平台支持（Windows, Linux, macOS）

## 📄 许可证

[MIT License](LICENSE)

## 🔗 相关链接

- [GitHub 仓库](https://github.com/itxaiohanglover/mcp-codestyle-server)
- [问题反馈](https://github.com/itxaiohanglover/mcp-codestyle-server/issues)
- [讨论区](https://github.com/itxaiohanglover/mcp-codestyle-server/discussions)
- [Codestyle Skill](skill/README.md)
