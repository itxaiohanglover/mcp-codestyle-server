# ContiNew CRUD 代码生成模板

基于 ContiNew 框架的 CRUD 增删改查代码生成模板，用于快速生成 RESTful API 后端代码和前端页面。

## 模板概述

本模板组包含完整的前后端代码生成模板，支持一键生成标准的增删改查功能代码。

### 后端模板 (Backend)

| 模板文件 | 说明 |
|---------|------|
| Controller.ftl | 控制层模板 - 生成 RESTful API Controller |
| Service.ftl | 服务层接口模板 |
| ServiceImpl.ftl | 服务层实现模板 |
| Mapper.ftl | 数据访问层 Mapper 接口模板 |
| MapperXml.ftl | MyBatis XML 映射文件模板 |
| Entity.ftl | 实体类模板 |
| Query.ftl | 查询条件封装类模板 |
| Req.ftl | 请求参数封装类模板 |
| Resp.ftl | 响应结果封装类模板 |
| DetailResp.ftl | 详情响应封装类模板 |
| Menu.ftl | 菜单 SQL 脚本模板 |

### 前端模板 (Frontend)

| 模板文件 | 说明 |
|---------|------|
| api.ftl | API 接口调用模板 |
| index.ftl | 列表页面模板 (Vue) |
| AddModal.ftl | 新增/编辑弹窗组件模板 |
| DetailDrawer.ftl | 详情抽屉组件模板 |

## 使用方法

1. 通过 `codestyleSearch` 工具搜索模板：
   - 关键词: CRUD, controller, service, mapper, entity, 增删改查, continew

2. 通过 `getTemplateByPath` 获取具体模板内容，传入模板路径如：
   - `continew/CRUD/1.0.0/backend/src/main/java/com/air/controller/Controller.ftl`
   - `continew/CRUD/1.0.0/frontend/src/views/index.ftl`

## 模板变量说明

主要变量包括：
- `packageName`: 项目根包名 (如: com.air.order)
- `subPackageName`: 子包名 (如: controller)
- `classNamePrefix`: 实体类命名前缀 (如: Order)
- `className`: 类名 (如: OrderController)
- `businessName`: 业务名称中文 (如: 订单)
- `apiModuleName`: API模块名 (如: system)

## 适用场景

- Spring Boot 后端项目
- Vue 3 + TypeScript 前端项目
- MyBatis Plus 数据访问层
- ContiNew Admin 管理系统