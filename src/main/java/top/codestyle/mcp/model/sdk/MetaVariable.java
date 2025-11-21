package top.codestyle.mcp.model.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 元变量
 *
 * @author 小航love666, movclantian
 * @since 2025-09-29
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaVariable {
    /**
     * 变量名
     */
    public String variableName;
    
    /**
     * 变量类型
     */
    public String variableType;
    
    /**
     * 变量注释说明
     */
    public String variableComment;
    
    /**
     * 变量示例值
     */
    private String example;
}