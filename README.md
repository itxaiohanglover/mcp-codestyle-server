# MCP 代码模板服务器

MCP Codestyle Server 是一个基于 Spring AI 实现的 Model Context Protocol (MCP) 服务器，为 IDE 和 AI 代理提供代码模板搜索和检索工具。该服务从本地缓存查找模板，并在缺失时自动从远程仓库下载元数据和文件进行修复。

## 核心特性
- **原生 MCP 工具**：`CodestyleService` 通过 `spring-ai-starter-mcp-server` 注册 `codestyleSearch` 和 `getTemplateByPath` 工具，STDIO 客户端（Cherry Studio、Cursor 等）可直接调用
- **自修复模板缓存**：`RepositoryConfig` + `TemplateService` 解析缓存位置并调用 `smartDownloadTemplate`，通过 SHA256 哈希比对决定是否下载最新资源
- **灵活搜索**：`SDKUtils` 解析每个 `meta.json`，支持关键词、同义词和精确路径搜索，并生成可读的目录树结构
- **提示词模板化**：`PromptService` 使用 `content-result.txt` 和 `search-result.txt` 渲染响应，确保变量和文件内容遵循统一布局
- **Maven风格目录**：采用 `groupId/artifactId/version/` 结构组织模板，支持多版本共存

## 技术栈
- Java 17, Maven 3.9+
- Spring Boot 3.2.5
- Spring AI MCP Server 1.1.0
- Jackson, Lombok

## 项目结构
```text
mcp-codestyle-server
|-- pom.xml
|-- src
|   |-- main
|   |   |-- java/top/codestyle/mcp
|   |   |   |-- McpServerApplication.java        # 应用程序入口
|   |   |   |-- config
|   |   |   |   |-- RepositoryConfig.java        # 仓库路径配置
|   |   |   |-- model
|   |   |   |   |-- meta/                        # 本地缓存元数据
|   |   |   |   |   |-- LocalMetaConfig.java     # 本地JSON配置模型
|   |   |   |   |   |-- LocalMetaInfo.java       # 本地模板元信息
|   |   |   |   |   |-- LocalMetaVariable.java   # 本地变量
|   |   |   |   |-- sdk/                         # SDK模型
|   |   |   |   |   |-- MetaInfo.java            # 模板元信息
|   |   |   |   |   |-- MetaVariable.java        # 模板变量
|   |   |   |   |   |-- RemoteMetaConfig.java    # 远程JSON配置模型
|   |   |   |   |-- tree/                        # 目录树模型
|   |   |   |       |-- Node.java                # 节点接口
|   |   |   |       |-- TreeNode.java            # 树节点实现
|   |   |   |-- service
|   |   |   |   |-- CodestyleService.java        # MCP工具实现
|   |   |   |   |-- TemplateService.java         # 模板搜索/下载/加载
|   |   |   |   |-- PromptService.java           # 提示词模板加载
|   |   |   |-- util
|   |   |       |-- SDKUtils.java                # 核心工具类（搜索/下载/SHA256）
|   |   |       |-- MetaInfoConvertUtil.java     # 元信息转换
|   |   |       |-- PromptUtils.java             # 目录树和变量格式化
|   |   |-- resources
|   |       |-- application.yml                  # 配置文件
|   |       |-- content-result.txt               # 模板内容提示词模板
|   |       |-- search-result.txt                # 搜索结果提示词模板
|   |-- test/java/top/codestyle/mcp
|       |-- service/CodestyleServiceTest.java    # 集成测试
|-- target/                                      # 构建输出
```

## 配置说明
核心配置位于 `src/main/resources/application.yml`，可通过 JVM 系统属性覆盖。

```yaml
spring:
  application:
    name: mcp-codestyle-server
  main:
    web-application-type: none      # 关闭Web服务器
    banner-mode: off                # 关闭启动横幅
  ai:
    mcp:
      server:
        name: mcp-codestyle-server  # MCP服务器名称
        version: 0.0.1              # 版本号
        stdio: true                 # 启用STDIO模式

repository:
  local-path: templates        # 本地基础路径
  remote-path: http://xxxxxxx       # 远程仓库地址（需配置）
  dir: C:/xxxx/codestyle-cache  # 可选，不配置则使用local-path/codestyle-cache
```

### 配置项说明：
- `repository.local-path`：本地缓存基础目录（可通过 `-Dcache.base-path` 覆盖）
- `repository.dir`：具体缓存文件夹，默认为 `<local-path>/codestyle-cache`
- `repository.remote-path`：**必须配置**，远程仓库基础URL

### 远程服务接口要求：
远程服务必须提供以下两个接口：

1. **获取模板元数据**
   ```
   GET {remoteBaseUrl}/api/mcp/search?templateKeyword=CRUD
   ```
   返回 `RemoteMetaConfig` JSON 格式：
   ```json
   {
     "groupId": "backend",
     "artifactId": "CRUD",
     "description": "完整的CRUD操作模板",
     "config": {
       "version": "1.0.1",
       "files": [
         {
           "filePath": "/src/main/java/com/air/controller",
           "filename": "Controller.ftl",
           "description": "控制器模板",
           "sha256": "abc123...",
           "inputVariables": [
             {
               "variableName": "className",
               "variableType": "String",
               "variableComment": "类名",
               "example": "UserController"
             }
           ]
         }
       ]
     }
   }
   ```

2. **下载模板ZIP**
   ```
   GET {remoteBaseUrl}/api/file/load?paths=/groupId/artifactId
   ```
   返回包含模板文件的 ZIP 压缩包，解压后目录结构应为：
   ```
   src/
     main/
       java/
         com/
           air/
             controller/
               Controller.ftl
   ```

## 快速开始

### 1. 前置条件
- JDK 17+
- Maven 3.9+（或使用项目自带的 `mvnw` / `mvnw.cmd`）

### 2. 克隆并构建
```bash
git clone https://github.com/itxaiohanglover/mcp-codestyle-server.git
cd mcp-codestyle-server
./mvnw clean package -DskipTests
```

### 3. 配置远程仓库地址
编辑 `src/main/resources/application.yml`，将 `repository.remote-path` 修改为实际的远程服务器地址：
```yaml
repository:
  remote-path: http://your-server.com  # 替换为实际地址
```

### 4. 运行 MCP 服务器
```bash
# Windows
java ^
  -Dspring.ai.mcp.server.stdio=true ^
  -Dspring.main.web-application-type=none ^
  -Dlogging.pattern.console= ^
  -Dcache.base-path=C:/mcp-cache ^
  -Drepository.remote-path=http://your-server.com ^
  -jar target/mcp-codestyle-server-0.0.1.jar

# Linux/macOS
java \
  -Dspring.ai.mcp.server.stdio=true \
  -Dspring.main.web-application-type=none \
  -Dlogging.pattern.console= \
  -Dcache.base-path=/mcp-cache \
  -Drepository.remote-path=http://your-server.com \
  -jar target/mcp-codestyle-server-0.0.1.jar
```

### 5. 配置 MCP 客户端

#### Cherry Studio 配置示例
在设置 -> MCP Servers 中添加：
```json
{
  "mcpServers": {
    "codestyleServer": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "C:/path/to/mcp-codestyle-server/target/mcp-codestyle-server-0.0.1.jar"
      ],
      "env": {}
    }
  }
}
```
![打开Cherry Studio](img/image.png)
注意实际jar路径和参数配置一致
![添加Json导入MCP](img/image-1.png)
添加成功
![成功添加](img/image-2.png)
通过配置按钮可以查看到两个工具已注册
![查看已注册工具](img/image-3.png)

#### Cursor 配置示例
在 `~/.cursor/mcp_settings.json` 中添加：
```json
{
  "mcpServers": {
    "codestyleServer": {
      "command": "java",
      "args": [
        "-Dspring.ai.mcp.server.stdio=true",
        "-Dspring.main.web-application-type=none",
        "-Dlogging.pattern.console=",
        "-jar",
        "/path/to/mcp-codestyle-server/target/mcp-codestyle-server-0.0.1.jar"
      ]
    }
  }
}
```
![在cursor中添加MCP](img/image-4.png)
启用服务器后，在聊天界面即可调用工具。

## MCP 工具

### 1. codestyleSearch - 搜索模板目录树
**参数：**
- `templateKeyword` (String): 模板关键词或同义词
  - 示例：`CRUD`、`controller`、`service`、`增删改查`、`控制器`

**响应：**
```
找到模板组: backend/CRUD

目录树:
backend/
  CRUD/
    v1.0.1/
      src/
        main/
          java/
            └── Controller.ftl
            └── Service.ftl

模板组介绍:
完整的CRUD操作模板，包含控制器、服务层、数据访问层等
```
**流程：**
1. 获取远程配置（`GET /api/mcp/search?templateKeyword=CRUD`）
2. 智能下载（如需要，通过SHA256比对决定）
3. 本地搜索（支持同义词匹配）
4. 构建目录树（`PromptUtils.buildTree`）
5. 格式化返回

### 2. getTemplateByPath - 获取模板详细内容
**参数：**
- `templatePath` (String): 完整模板路径
  - 格式：`groupId/artifactId/version/filePath/filename`
  - 示例：`backend/CRUD/v1.0.1/src/main/java/com/air/controller/Controller.ftl`

**响应：**
```
#文件名：backend/CRUD/v1.0.1/src/main/java/com/air/controller/Controller.ftl
#文件变量：
- className: 类名（示例：UserController）[String]
- packageName: 包名（示例：com.air.controller）[String]
#文件内容：
package ${packageName};

public class ${className} {
    // CRUD方法
}
```
**流程：**
1. 精确路径搜索本地缓存
2. 如未找到，解析路径提取 `artifactId` 并触发智能下载
3. 重新搜索
4. 加载模板内容
5. 格式化返回（变量说明 + 模板代码）

## 模板仓库结构

### 本地缓存目录结构
```
codestyle-cache/
└── backend/                    # groupId
    └── CRUD/                   # artifactId
        ├── meta.json          # 元数据配置
        └── v1.0.1/            # version
            └── src/
                └── main/
                    └── java/
                        └── com/
                            └── air/
                                └── controller/
                                    └── Controller.ftl
```

### meta.json 格式（本地）
```json
{
  "groupId": "backend",
  "artifactId": "CRUD",
  "configs": [
    {
      "version": "1.0.1",
      "files": [
        {
          "filePath": "/src/main/java/com/air/controller",
          "filename": "Controller.ftl",
          "description": "控制器模板",
          "sha256": "abc123...",
          "inputVariables": [
            {
              "variableName": "className",
              "variableType": "String",
              "variableComment": "类名",
              "example": "UserController"
            }
          ]
        }
      ]
    }
  ]
}
```

**特点：**
- 支持多版本共存（`configs` 数组）
- SHA256 校验保证文件完整性
- 精确的文件路径和变量描述

## 提示词模板

### content-result.txt（模板内容）
```
#文件名：%{s}
#文件变量：
%{s}
#文件内容：
%{s}
```
- 3个占位符：文件名、变量列表、模板内容

### search-result.txt（搜索结果）
```
找到模板组: %{s}

目录树:
%{s}
模板组介绍:
%{s}
```
- 3个占位符：groupId/artifactId、目录树、描述

可编辑这些文件以适配不同 MCP 客户端的响应风格。

## 同义词映射

`SDKUtils.SYNONYM_MAP` 支持中英文混合搜索：

**后端关键词：**
- `controller` → 控制器、控制层、restful
- `service` → 服务、服务层、业务逻辑、业务层
- `mapper` → 持久层、数据访问层、dao
- `entity` → 实体、实体类

**前端关键词：**
- `index` → 首页、主页、索引页
- `list` → 列表页、清单
- `form` → 表单、表单页、输入页
- `modal` → 弹窗、对话框、模态框

**通用关键词：**
- `crud` → 增删改查、基础操作、数据操作
- `backend` → 后端、服务端、server
- `frontend` → 前端、客户端、ui、界面

可在 `SDKUtils` 中扩展新的关键词系列。

## 开发与测试

### 运行集成测试
```bash
mvn test
```

或运行 `CodestyleServiceTest.main()` 方法：
```java
// 通过 STDIO 启动 JAR 并调用 MCP 工具
CodestyleServiceTest.main(new String[]{});
```

### 扩展新模板
1. 在远程仓库添加新的模板 ZIP 和对应的 JSON 配置
2. 确保 `meta.json` 中的 `sha256` 与实际文件哈希一致
3. 扩展 `SYNONYM_MAP` 添加新关键词
4. 运行测试验证端到端流程

### 调试技巧
- 查看日志：移除 `-Dlogging.pattern.console=` 参数
- 检查缓存：查看 `repository.dir` 配置的目录
- 验证远程接口：使用 `curl` 或 Postman 测试远程 API
- 测试 SHA256：`SDKUtils.shouldUpdateTemplate` 会打印比对结果

## 常见问题

### Q: 如何清理本地缓存？
A: 删除 `repository.dir` 配置的目录，下次运行时会自动重新下载。

### Q: 如何支持多用户？
A: 为每个用户配置不同的 `cache.base-path`，或在 `groupId` 前添加用户标识。

### Q: 远程仓库不可用时如何处理？
A: 系统会继续使用本地缓存，并返回友好的错误提示。

### Q: 如何添加新的同义词？
A: 编辑 `SDKUtils.SYNONYM_MAP`，在对应的关键词列表中添加新词。

## 许可证
基于 [MIT License](LICENSE) 发布。

## 作者
artboy(itxaiohanglover)
Kanttha
movclantian

## 更新日志
- **v0.0.1** (2025-11-23)
  - 初始版本
  - 支持 `codestyleSearch` 和 `getTemplateByPath` 工具
  - Maven风格目录结构
  - SHA256完整性校验
  - 自动修复机制
