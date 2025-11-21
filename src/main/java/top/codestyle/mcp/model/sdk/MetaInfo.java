package top.codestyle.mcp.model.sdk;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 元信息
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Data
@NoArgsConstructor
public class MetaInfo {
    /**
     * 模板ID
     */
    private Long id;
    
    /**
     * 组织名(如: artboy)
     */
    private String groupId;
    
    /**
     * 模板组名(如: CRUD)
     */
    private String artifactId;
    
    /**
     * 模板描述(如: 控制层)
     */
    private String description;
    
    /**
     * 文件SHA256哈希值
     */
    private String sha256;
    
    /**
     * 版本号(如: 1.0.0)
     */
    private String version;
    
    /**
     * 模板文件名(如: Controller.java.ftl)
     */
    private String filename;
    
    /**
     * 模板文件所在目录路径(如: /src/main/java/com/air/controller)
     */
    private String filePath;
    
    /**
     * 模板文件完整路径(如: /src/main/java/com/air/controller/Controller.java.ftl)
     */
    private String path;

    /**
     * 模板输入变量列表
     */
    private List<MetaVariable> inputVariables;
}