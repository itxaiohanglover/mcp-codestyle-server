package top.codestyle.mcp.model.sdk;

import lombok.Data;

import java.util.List;

/**
 * 远程 API 返回的 meta.json 配置结构
 * config 字段为单个对象
 *
 * @author movclantian
 * @since 2025-11-22
 */
@Data
public class RemoteMetaConfig {
    /**
     * 组织名(用户名)
     */
    private String groupId;
    
    /**
     * 模板组名
     */
    private String artifactId;
    
    /**
     * 模板组总体描述
     */
    private String description;
    
    /**
     * 单个版本配置对象
     */
    private Config config;

    @Data
    public static class Config {
        /**
         * 版本号(如 "1.0")
         */
        private String version;
        
        /**
         * 该版本的文件列表
         */
        private List<FileInfo> files;
    }

    @Data
    public static class FileInfo {
        /**
         * 文件路径(如 "src/main/java/com/air/controller")
         */
        private String filePath;
        
        /**
         * 文件说明
         */
        private String description;
        
        /**
         * 文件名(如 "Controller.java.ftl")
         */
        private String filename;
        
        /**
         * 输入变量列表
         */
        private List<MetaVariable> inputVarivales;
        
        /**
         * 文件SHA256哈希值
         */
        private String sha256;
    }
}
