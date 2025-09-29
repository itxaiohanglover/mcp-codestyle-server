package top.codestyle.mcp.model.sdk;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 元信息
 */
@Data
@NoArgsConstructor
public class MetaInfo {
    private Long id; // ID
    private String groupId; //组织名: artboy
    private String artifactId; //模板组：CRUD
    private String description; // 备注：控制层
    private String sha256; // SHA256值；722f185c48bed892d6fa12e2b8bf1e5f8200d4a70f522fb62b6caf13cb74e
    private String version; //版本号: v1.0.0
    private String filename; // 模板名称：Controller.java.ftl
    private String filePath; // 模板目录；/src/main/java/com/air/controller
    private List<MetaVariable> metaVariables; // 模板变量说明；
}