package top.codestyle.mcp.model.entity;

import lombok.Data;

import java.util.List;


@Data
public  class MetaItem {
    private String filePath;
    private String version;
    private String description;
    private String filename;
    private List<MetaVariable> inputVarivales;
    private String sha256;
}

