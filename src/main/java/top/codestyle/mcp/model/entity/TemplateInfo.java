package top.codestyle.mcp.model.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
public class TemplateInfo {
    private Long id; // ID
    private String name; // 名称：Controller.java.ftl
    private Long size; // 大小(字节)；4096
    private String parent_path; // 上级目录；/src/main/java/com/air/controller。默认为:/
    private String path; // 路径；/src/main/java/com/air/controller/Controller.java.ftl
    private Integer type; // 类型：0-目录；1：文件
    private String comment; // 备注：控制层
    private List<InputVariable> inputVarivales; // 模板变量说明；
    private List<String> tags; // 标签; ["Controller", "CRUD"]
    private String sha256; // SHA256值；722f185c48bed892d6fa12e2b8bf1e5f8200d4a70f522fb62b6caf13cb74e
    private Long storage_id; //存储ID
    private String content; // 内容
}
