package top.codestyle.mcp.service;

import cn.hutool.db.meta.Column;
import cn.hutool.db.meta.Table;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.GeneratorProperties;
import top.codestyle.mcp.enums.DatabaseType;
import top.codestyle.mcp.model.entity.FieldConfigDO;
import top.codestyle.mcp.model.req.ToolReq;
import top.codestyle.mcp.model.resp.ToolResp;

import java.util.*;

/**
 * @author 文艺倾年
 * @Description
 */
@Slf4j
@Service
public class GeneratorService {
    /**
     * TODO
     */
    @Tool(name = "get-tablemeta-sql-by-tablename", description = "根据表名获取表元数据SQL")
    public String getFieldConfigSQL(@ToolParam(description = "tableName") String tableName) {
        // 提供一个SQL语句，由数据库执行获取元数据，组装成Table元素
        // Table table = Table.create(tableName);
        // table.setCatalog(catalog);
        // table.setSchema(schema);
        // 这里需要重新构建，基于不同数据库厂商。
        String result = null;
        try {
            // TODO 待修改
            result = String.format("SELECT * FROM %s", tableName);
            System.out.println(result);
            log.info("mcp server run getFieldConfigSQL, result = {}", result);
        } catch (Exception e) {
            log.error("call mcp server failed, e:\n", e);
            return String.format("调用服务失败，异常[%s]", e.getMessage());
        }
        return result;
    }

    /**
     * TODO 根据表信息获取字段配置
     * @return
     */
    @Tool(name = "get-field-config-by-table-info", description = "根据表信息获取字段配置")
    public String getFieldConfigByTableInfo(@ToolParam(description = "表配置") Table table, @ToolParam(description = "databaseProductName ") String databaseProductName ) {
        String result = null;
        List<FieldConfigDO> fieldConfigList = new ArrayList<>();
        // 1.获取数据表列信息
        Collection<Column> columnList = table.getColumns();
        // 2.获取数据库对应的类型映射配置
        // 2.1根据产品名称获取对应的数据库类型枚举
        DatabaseType databaseType = DatabaseType.get(databaseProductName);
        // 2.2根据数据库类型获取类型映射配置
        GeneratorProperties generatorProperties = null;
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
     * TODO 获取Code模板
     */
    @Tool(name = "get-code-template", description = "获取Code模板")
    public String getCodeTemplate() {
        return null;
    }

    /**
     * TODO 获取SQL模板
     */
    @Tool(name = "get-sql-template", description = "获取SQL模板")
    public String getSqlTemplate() {
        return null;
    }

    /**
     * TODO 获取模板对外部依赖版本进行的限制列表
     */
    @Tool(name = "get-template-dependency-versions", description = "获取模板对外部依赖版本进行的限制列表")
    public String getTemplateDependencyVersions() {
        return null;
    }

    /**
     * 供测试用
     */
    @Tool(name = "get-weather", description = "Get weather information by city name.")
    public String getWeather(ToolReq cityName) {
        ToolResp result = null;
        try {
            result = new ToolResp();
            result.setToolParamInfo(cityName.getToolParamInfo());
            log.info("mcp server run getWeather, result = {}", result);
        } catch (Exception e) {
            log.error("call mcp server failed, e:\n", e);
            return String.format("调用服务失败，异常[%s]", e.getMessage());
        }
        return result.toString();
    }
}
