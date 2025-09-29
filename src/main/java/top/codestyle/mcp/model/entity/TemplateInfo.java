package top.codestyle.mcp.model.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
public class TemplateInfo {
    private Long id; // ID
    private String filename; // 模板名称：Controller.java.ftl
    private String file_path; // 模板目录；/src/main/java/com/air/controller
    private String path; // 路径；/src/main/java/com/air/controller/Controller.java.ftl
    private String version; //版本号: v1.0.0
    private String groupId; //组织名: artboy
    private String artifactId; //模板组：CRUD
    private String description; // 备注：控制层
    private List<InputVariable> inputVarivales; // 模板变量说明；
    private String sha256; // SHA256值；722f185c48bed892d6fa12e2b8bf1e5f8200d4a70f522fb62b6caf13cb74e
    private String content; //文件内容
}
