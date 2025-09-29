package top.codestyle.mcp.model.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   // ← 忽略 JSON 中不认识的全部字段
public class MetaVariable {
    public String variableName;
    public String variableType;
    public String variableComment;
    private String example;
}