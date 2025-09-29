package top.codestyle.mcp.model.meta;

import lombok.Data;
import top.codestyle.mcp.model.sdk.MetaInfo;


@Data
public class LocalMetaInfo extends MetaInfo {
//    private Long id; // ID
//    private String version; //版本号: v1.0.0
//    private String groupId; //组织名: artboy
//    private String artifactId; //模板组：CRUD
//    private String filePath;
//    private String description;
//    private String filename;
//    private List<LocalMetaVariable> localMetaVariables;
//    private String sha256;
    /**
     * 模板内容
     */
    private String templateContent;
}