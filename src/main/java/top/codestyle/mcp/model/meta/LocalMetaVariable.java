package top.codestyle.mcp.model.meta;

import lombok.Data;

@Data
public class LocalMetaVariable {
    private String variableName;
    private String variableType;
    private String variableComment;
    private String example;
}