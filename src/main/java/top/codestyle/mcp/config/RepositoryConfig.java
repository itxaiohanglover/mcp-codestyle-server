package top.codestyle.mcp.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓库配置类
 * 管理仓库路径和缓存目录配置
 *
 * @author 文艺倾年
 * @since 2025/9/20
 */
@Configuration
public class RepositoryConfig {

    /**
     * 本地基础路径，默认使用系统临时目录
     * 可通过JVM参数 -Dcache.base-path=自定义路径 覆盖
     */
    @Value("${repository.local-path:${java.io.tmpdir}}")
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
    @Value("${repository.dir:${repository.local-path}/codestyle-cache}")
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
        return repositoryDir;
    }

    /**
     * 创建仓库目录并确保其存在
     * 当指定路径无法创建时，自动使用系统临时目录作为备选
     * @return 仓库目录路径
     */
    @Bean
    public Path repositoryDirectory() {
        // 记录当前操作系统类型
        String osName = System.getProperty("os.name").toLowerCase();
        System.out.println("当前操作系统: " + osName);
        try {
            // 规范化路径，确保跨平台兼容性
            String normalizedRepoDir = normalizePath(repositoryDir);
            Path repoPath = Paths.get(normalizedRepoDir);

            // 如果目录不存在，则创建目录
            if (!Files.exists(repoPath)) {
                Files.createDirectories(repoPath);
            }

            System.out.println("仓库目录已创建: " + repoPath.toAbsolutePath());
            return repoPath;
        } catch (Exception e) {
            // 如果创建失败，使用系统临时目录作为备选
            String fallbackTempDir = System.getProperty("java.io.tmpdir") + File.separator + "codestyle-cache";
            Path fallbackPath = Paths.get(fallbackTempDir);
            try {
                if (!Files.exists(fallbackPath)) {
                    Files.createDirectories(fallbackPath);
                }
                System.err.println("使用备选仓库目录: " + fallbackPath.toAbsolutePath());
                return fallbackPath;
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new RuntimeException("无法创建仓库目录", ex);
            }
        }
    }

    /**
     * 规范化路径字符串，确保在不同操作系统上的兼容性
     * @param path 原始路径字符串
     * @return 规范化后的路径字符串
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // 处理不同操作系统的路径分隔符
        String normalizedPath = path.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        // 确保路径不会因为多个连续的分隔符而出问题
        while (normalizedPath.contains(File.separator + File.separator)) {
            normalizedPath = normalizedPath.replace(File.separator + File.separator, File.separator);
        }

        return normalizedPath;
    }
}