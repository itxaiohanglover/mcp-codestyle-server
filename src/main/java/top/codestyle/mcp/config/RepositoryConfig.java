package top.codestyle.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import top.codestyle.mcp.util.SDKUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓库配置类
 * 管理仓库路径和缓存目录配置
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Configuration
public class RepositoryConfig {

    /**
     * 本地基础路径，默认使用系统临时目录
     * 可通过JVM参数 -Dcache.base-path=自定义路径 覆盖
     */
    @Value("${cache.base-path:${repository.local-path:${java.io.tmpdir}}}")
    private String localPath;

    /**
     * 远程仓库地址
     */
    @Value("${repository.remote-path}")
    private String remotePath;

    /**
     * 仓库目录路径
     * 默认在基础路径下创建codestyle-cache目录
     */
    @Value("${repository.dir:}")
    private String repositoryDir;

    /**
     * 获取本地基础路径
     */
    public String getLocalPath() {
        return localPath;
    }

    /**
     * 获取远程仓库地址
     */
    public String getRemotePath() {
        return remotePath;
    }

    /**
     * 获取仓库目录路径
     */
    public String getRepositoryDir() {
        // 如果未配置repository.dir,则使用localPath + codestyle-cache
        if (repositoryDir == null || repositoryDir.isEmpty()) {
            return localPath + File.separator + "codestyle-cache";
        }
        return repositoryDir;
    }

    /**
     * 创建仓库目录Bean
     * 确保仓库目录存在,创建失败时自动降级到系统临时目录
     *
     * @return 仓库目录路径
     */
    @Bean
    public Path repositoryDirectory() {
        try {
            String normalizedRepoDir = SDKUtils.normalizePath(getRepositoryDir());
            Path repoPath = Paths.get(normalizedRepoDir);

            if (!Files.exists(repoPath)) {
                Files.createDirectories(repoPath);
            }
            return repoPath;
        } catch (Exception e) {
            String fallbackTempDir = System.getProperty("java.io.tmpdir") + File.separator + "codestyle-cache";
            Path fallbackPath = Paths.get(fallbackTempDir);
            try {
                if (!Files.exists(fallbackPath)) {
                    Files.createDirectories(fallbackPath);
                }
                return fallbackPath;
            } catch (Exception ex) {
                throw new RuntimeException("无法创建仓库目录", ex);
            }
        }
    }

}