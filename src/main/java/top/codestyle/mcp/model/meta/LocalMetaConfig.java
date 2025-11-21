package top.codestyle.mcp.model.meta;

import lombok.Data;
import top.codestyle.mcp.model.sdk.MetaVariable;

import java.util.List;

/**
 * 本地缓存的 meta.json 配置结构
 * 支持多版本的 configs 数组
 *
 * @author movclantian
 * @since 2025-11-22
 */
@Data
public class LocalMetaConfig {
    /**
     * 组织名(用户名)
     */
    private String groupId;
    
    /**
     * 模板组名
     */
    private String artifactId;
    
    /**
     * 多版本配置列表
     */
    private List<Config> configs;

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
        private List<MetaVariable> inputVariables;
        
        /**
         * 文件SHA256哈希值
         */
        private String sha256;
    }
}
