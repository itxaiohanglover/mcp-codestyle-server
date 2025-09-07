package top.codestyle.mcp.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.meta.Column;
import cn.hutool.db.meta.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.GeneratorProperties;
import top.codestyle.mcp.config.ProjectProperties;
import top.codestyle.mcp.constant.StringConstants;
import top.codestyle.mcp.enums.DatabaseType;
import top.codestyle.mcp.model.entity.FieldConfigDO;
import top.codestyle.mcp.model.entity.GenConfigDO;
import top.codestyle.mcp.model.entity.InnerGenConfigDO;
import top.codestyle.mcp.model.req.ToolReq;
import top.codestyle.mcp.model.resp.GeneratePreviewResp;
import top.codestyle.mcp.model.resp.ToolResp;
import top.codestyle.mcp.util.TemplateUtils;

import java.io.File;
import java.util.*;

/**
 * @author 文艺倾年
 * @Description
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratorService {

    public enum DbType {
        MYSQL, ORACLE, SQLSERVER, POSTGRESQL
    }
    /** 表名校验：字母开头，字母/数字/下划线 */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    @Autowired
    private DataSource dataSource;


    private final GeneratorProperties generatorProperties;
    private final ProjectProperties projectProperties;

    /**
     * 初始化Init
     */
    @Tool(name = "")
    public void init() {
        // 1.获取项目配置
        ProjectProperties projectProperties = this.projectProperties;
        // 2.获取生成器配置
        GeneratorProperties generatorProperties = this.generatorProperties;
    }

    /**
     * 仅返回 SQL，不执行
     * @param tableName 表名
     * @param dbType    数据库类型
     * @return 查询表字段配置的 SQL（含注释）
     * @throws IllegalArgumentException 表名非法
     */
    @Tool(name = "get-tablemeta-sql-by-tablename", description = "根据表名获取表元数据SQL")
    public String getFieldConfigSQL(@ToolParam(description = "tableName") String tableName, @ToolParam(description = "DbType")DbType dbType) {
        //非法表名检验
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法表名，只允许字母、数字、下划线，且以字母开头");
        }
        return switch (dbType) {
            case MYSQL -> "SELECT COLUMN_NAME AS col_name, DATA_TYPE AS data_type, " +
                    "COLUMN_TYPE AS full_type, IS_NULLABLE AS is_nullable, " +
                    "COLUMN_DEFAULT AS col_default, COLUMN_COMMENT AS col_comment " +
                    "FROM information_schema.columns " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                    "ORDER BY ORDINAL_POSITION";
            case ORACLE -> "SELECT COLUMN_NAME AS col_name, DATA_TYPE AS data_type, " +
                    "DATA_TYPE || '(' || DATA_LENGTH || ')' AS full_type, " +
                    "NULLABLE AS is_nullable, DATA_DEFAULT AS col_default, " +
                    "COMMENTS AS col_comment " +
                    "FROM ALL_TAB_COLUMNS c " +
                    "LEFT JOIN ALL_COL_COMMENTS m ON c.TABLE_NAME = m.TABLE_NAME AND c.COLUMN_NAME = m.COLUMN_NAME " +
                    "WHERE c.TABLE_NAME = '" + tableName.toUpperCase() + "' " +
                    "ORDER BY c.COLUMN_ID";
            case SQLSERVER -> "SELECT c.name AS col_name, t.name AS data_type, " +
                    "t.name + '(' + CAST(c.max_length AS VARCHAR) + ')' AS full_type, " +
                    "CASE WHEN c.is_nullable = 1 THEN 'YES' ELSE 'NO' END AS is_nullable, " +
                    "dc.definition AS col_default, ep.value AS col_comment " +
                    "FROM sys.columns c " +
                    "JOIN sys.types t ON c.user_type_id = t.user_type_id " +
                    "LEFT JOIN sys.default_constraints dc ON c.default_object_id = dc.object_id " +
                    "LEFT JOIN sys.extended_properties ep ON ep.major_id = c.object_id AND ep.minor_id = c.column_id AND ep.name = 'MS_Description' " +
                    "WHERE OBJECT_NAME(c.object_id) = '" + tableName + "' " +
                    "ORDER BY c.column_id";
            case POSTGRESQL ->
                    "SELECT a.attname AS col_name, pg_catalog.format_type(a.atttypid, a.atttypmod) AS full_type, " +
                            "pg_catalog.format_type(a.atttypid, NULL) AS data_type, " +
                            "CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS is_nullable, " +
                            "pg_get_expr(d.adbin, d.adrelid) AS col_default, " +
                            "col_description(a.attrelid, a.attnum) AS col_comment " +
                            "FROM pg_attribute a " +
                            "LEFT JOIN pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum " +
                            "WHERE a.attrelid = '" + tableName + "'::regclass AND a.attnum > 0 AND NOT a.attisdropped " +
                            "ORDER BY a.attnum";
            default -> throw new IllegalArgumentException("暂不支持的数据库类型");
        };
    }

    /**
     * TODO 根据表信息获取字段配置
     * @return
     */
    @Tool(name = "get-field-config-by-table-info", description = "根据表信息获取字段配置")

    public String getFieldConfigByTableInfo(@ToolParam(description = "表配置") TableEntity table, @ToolParam(description = "databaseProductName ") String databaseProductName) {
        String result = null;
        List<FieldConfigDO> fieldConfigList = new ArrayList<>();

        // 1.获取数据表列信息
        Collection<Column> columnList = table.getColumns();
        // 2.获取数据库对应的类型映射配置
        // 2.1根据产品名称获取对应的数据库类型枚举
        DatabaseType databaseType = DatabaseType.get(databaseProductName);
        // 2.2根据数据库类型获取类型映射配置
        Map<String, List<String>> typeMappingMap = generatorProperties.getTypeMappings().get(databaseType);
        Set<Map.Entry<String, List<String>>> typeMappingEntrySet = typeMappingMap.entrySet();
        int i = 1; // 字段排序计数器

        // 遍历数据库表列信息，创建或更新字段配置
        for (Column column : columnList) {
            // 创建新配置
            FieldConfigDO fieldConfig = new FieldConfigDO(column);

            // 根据数据库类型映射确定字段类型
            String fieldType = typeMappingEntrySet.stream()
                    .filter(entry -> entry.getValue().contains(fieldConfig.getColumnType()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            fieldConfig.setFieldType(fieldType);
            fieldConfig.setFieldSort(i++); // 设置字段排序
            fieldConfigList.add(fieldConfig);
        }
        System.out.println(fieldConfigList);

        return result;
    }

    /**
     * 根据字段配置生成代码
     */
    @Tool(name = "generate-code-by-field-config", description = "根据字段配置生成代码")
    public String generateCodeByFieldConfig(@ToolParam(description = "字段配置") List<FieldConfigDO> fieldConfigList, @ToolParam(description = "databaseProductName ") String databaseProductName) {
        List<GeneratePreviewResp> generatePreviewList = new ArrayList<>();
        // TODO 封装异常抛出类
        // 根据字段配置渲染代码
        GenConfigDO genConfig = new GenConfigDO(); // TODO
        InnerGenConfigDO innerGenConfig = new InnerGenConfigDO(genConfig);
        String classNamePrefix = innerGenConfig.getClassNamePrefix();
        Map<String, GeneratorProperties.TemplateConfig> templateConfigMap = generatorProperties.getTemplateConfigs();
        for (Map.Entry<String, GeneratorProperties.TemplateConfig> templateConfigEntry : templateConfigMap.entrySet()) {
            GeneratorProperties.TemplateConfig templateConfig = templateConfigEntry.getValue();
            // 移除需要忽略的字段
            innerGenConfig.setFieldConfigs(fieldConfigList.stream()
                    .filter(fieldConfig -> !StrUtil.equalsAny(fieldConfig.getFieldName(), templateConfig
                            .getExcludeFields()))
                    .toList());
            // 预处理配置
            this.pretreatment(innerGenConfig);
            // 处理其他配置
            innerGenConfig.setSubPackageName(templateConfig.getPackageName());
            String classNameSuffix = templateConfigEntry.getKey();
            String className = classNamePrefix + classNameSuffix;
            innerGenConfig.setClassName(className);
            boolean isBackend = templateConfig.isBackend();
            String extension = templateConfig.getExtension();
            GeneratePreviewResp generatePreview = new GeneratePreviewResp();
            generatePreview.setBackend(isBackend);
            generatePreviewList.add(generatePreview);
            String fileName = className + extension;
            if (!isBackend) {
                fileName = ".vue".equals(extension) && "index".equals(classNameSuffix)
                        ? "index.vue"
                        : this.getFrontendFileName(classNamePrefix, className, extension);
            }
            generatePreview.setFileName(fileName);
            generatePreview.setContent(TemplateUtils.render(templateConfig.getTemplatePath(), BeanUtil
                    .beanToMap(innerGenConfig)));
            this.setPreviewPath(generatePreview, innerGenConfig, templateConfig);
        }
        return null;
    }

    /**
     * TODO 获取Code模板
     */
    @Tool(name = "get-code-template", description = "获取Code模板")
    public String getCodeTemplate() {
        String codeTemplate = """
package ${packageName}.${subPackageName};

import top.continew.starter.extension.crud.enums.Api;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.*;

import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
import top.air.backend.common.base.BaseController;
import ${packageName}.model.query.${classNamePrefix}Query;
import ${packageName}.model.req.${classNamePrefix}Req;
import ${packageName}.model.resp.${classNamePrefix}DetailResp;
import ${packageName}.model.resp.${classNamePrefix}Resp;
import ${packageName}.service.${classNamePrefix}Service;

/**
 * ${businessName}管理 API
 *
 * @author ${author}
 * @since ${datetime}
 */
@Tag(name = "${businessName}管理 API")
@RestController
@CrudRequestMapping(value = "/${apiModuleName}/${apiName}", api = {Api.PAGE, Api.DETAIL, Api.ADD, Api.UPDATE, Api.DELETE, Api.EXPORT})
public class ${className} extends BaseController<${classNamePrefix}Service, ${classNamePrefix}Resp, ${classNamePrefix}DetailResp, ${classNamePrefix}Query, ${classNamePrefix}Req> {}
        """.strip();
        return codeTemplate;
    }

//    /**
//     * TODO 获取SQL模板
//     */
//    @Tool(name = "get-sql-template", description = "获取SQL模板")
//    public String getSqlTemplate() {
//        String SQLTemplate = """
//-- ${businessName}管理菜单
//INSERT INTO `sys_menu`
//(`title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`, `update_user`, `update_time`)
//VALUES
//('${businessName}管理', 1000, 2, '/${apiModuleName}/${apiName}', '${classNamePrefix}', '${apiModuleName}/${apiName}/index', NULL, NULL, b'0', b'0', b'0', NULL, 1, 1, 1, NOW(), NULL, NULL);
//
//SET @parentId = LAST_INSERT_ID();
//
//-- ${businessName}管理按钮
//INSERT INTO `sys_menu`
//(`title`, `parent_id`, `type`, `path`, `name`, `component`, `redirect`, `icon`, `is_external`, `is_cache`, `is_hidden`, `permission`, `sort`, `status`, `create_user`, `create_time`, `update_user`, `update_time`)
//VALUES
//('列表', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:list', 1, 1, 1, NOW(), NULL, NULL),
//('详情', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:detail', 2, 1, 1, NOW(), NULL, NULL),
//('新增', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:add', 3, 1, 1, NOW(), NULL, NULL),
//('修改', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:update', 4, 1, 1, NOW(), NULL, NULL),
//('删除', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:delete', 5, 1, 1, NOW(), NULL, NULL),
//('导出', @parentId, 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '${apiModuleName}:${apiName}:export', 6, 1, 1, NOW(), NULL, NULL);
//        """.strip();
//        return SQLTemplate;
//    }

    /**
     * TODO 获取模板对外部依赖版本进行的限制列表
     */
    @Tool(name = "get-template-dependency-versions", description = "获取模板对外部依赖版本进行的限制列表")
    public String getTemplateDependencyVersions(String dependencyName) {
        String templateDependencyVersions = """
            {
            }
        """;
        return null;
    }

    /**
     * 供测试用
     */
    @Tool(name = "get-weather", description = "Get weather information by city name.")
    public String getWeather(ToolReq toolReq) {
        ToolResp result = null;
        try {
            result = new ToolResp();
            result.setToolParamInfo(toolReq.getToolParamInfo());
            result.setToolParamInfo("晴天！");
            log.info("mcp server run getWeather, result = {}", result);
        } catch (Exception e) {
            log.error("call mcp server failed, e:\n", e);
            return String.format("调用服务失败，异常[%s]", e.getMessage());
        }
        return result.toString();
    }


    private static final List<String> TIME_PACKAGE_CLASS = Arrays.asList("LocalDate", "LocalTime", "LocalDateTime");

    /**
     * 预处理生成配置
     *
     * @param genConfig 生成配置
     */
    private void pretreatment(InnerGenConfigDO genConfig) {
        List<FieldConfigDO> fieldConfigList = genConfig.getFieldConfigs();
        // 统计部分特殊字段特征
        Set<String> dictCodeSet = new HashSet<>();
        for (FieldConfigDO fieldConfig : fieldConfigList) {
            String fieldType = fieldConfig.getFieldType();
            // 必填项
            if (Boolean.TRUE.equals(fieldConfig.getIsRequired())) {
                genConfig.setHasRequiredField(true);
            }
            // 数据类型
            if ("BigDecimal".equals(fieldType)) {
                genConfig.setHasBigDecimalField(true);
            }
            if (TIME_PACKAGE_CLASS.contains(fieldType)) {
                genConfig.setHasTimeField(true);
            }
            // 字典码
            if (StrUtil.isNotBlank(fieldConfig.getDictCode())) {
                genConfig.setHasDictField(true);
                dictCodeSet.add(fieldConfig.getDictCode());
            }
        }
        genConfig.setDictCodes(dictCodeSet);
    }

    /**
     * 构建后端包路径
     *
     * @param genConfig 生成配置
     * @return 后端包路径
     */
    private String buildBackendBasicPackagePath(GenConfigDO genConfig) {
        // 例如：continew-admin/continew-system/src/main/java/top/continew/admin/system
        return String.join(File.separator, projectProperties.getAppName(), projectProperties.getAppName(), genConfig
                .getModuleName(), "src", "main", "java", genConfig.getPackageName()
                .replace(StringConstants.DOT, File.separator));
    }

    /**
     * 获取前端文件名
     *
     * @param classNamePrefix 类名前缀
     * @param className       类名
     * @param extension       扩展名
     * @return 前端文件名
     */
    private String getFrontendFileName(String classNamePrefix, String className, String extension) {
        return (".ts".equals(extension) ? StrUtil.lowerFirst(classNamePrefix) : className) + extension;
    }

    private void setPreviewPath(GeneratePreviewResp generatePreview,
                                InnerGenConfigDO genConfig,
                                GeneratorProperties.TemplateConfig templateConfig) {
        // 获取前后端基础路径
        String backendBasicPackagePath = this.buildBackendBasicPackagePath(genConfig);
        String frontendBasicPackagePath = String.join(File.separator, projectProperties.getAppName(), projectProperties
                .getAppName() + "-ui");
        String packagePath;
        if (generatePreview.isBackend()) {
            // 例如：continew-admin/continew-system/src/main/java/top/continew/admin/system/service/impl
            packagePath = String.join(File.separator, backendBasicPackagePath, templateConfig.getPackageName()
                    .replace(StringConstants.DOT, File.separator));
        } else {
            // 例如：continew-admin/continew-admin-ui/src/views/system
            packagePath = String.join(File.separator, frontendBasicPackagePath, templateConfig.getPackageName()
                    .replace(StringConstants.SLASH, File.separator), genConfig.getApiModuleName());
            // 例如：continew-admin/continew-admin-ui/src/views/system/user
            packagePath = ".vue".equals(templateConfig.getExtension())
                    ? packagePath + File.separator + StrUtil.lowerFirst(genConfig.getClassNamePrefix())
                    : packagePath;
        }
        generatePreview.setPath(packagePath);
    }
}
