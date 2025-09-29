package top.codestyle.mcp.model.entity;

import lombok.Data;

@Data
public  class MetaVariable {
    private String variableName;
    private String variableType;
    private String variableComment;
    private String example;
}