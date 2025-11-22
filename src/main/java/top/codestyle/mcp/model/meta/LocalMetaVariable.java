package top.codestyle.mcp.model.meta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地缓存的元变量结构
 *
 * @author 小航love666, movclantian
 * @since 2025-09-29
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalMetaVariable {
    /**
     * 变量名
     */
    private String variableName;
    
    /**
     * 变量类型
     */
    private String variableType;
    
    /**
     * 变量注释说明
     */
    private String variableComment;
    
    /**
     * 变量示例值
     */
    private String example;
}