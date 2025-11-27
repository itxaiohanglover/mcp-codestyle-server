package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;
import top.codestyle.mcp.util.MetaInfoConvertUtil;
import top.codestyle.mcp.util.SDKUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模板服务
 * 提供模板搜索、远程配置获取和智能下载功能
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Slf4j
@Service
public class TemplateService {

    @Autowired
    private RepositoryConfig repositoryConfig;

    /**
     * 根据groupId和artifactId搜索指定模板组
     *
     * @param groupId    组ID
     * @param artifactId 项目ID
     * @return 匹配的模板元信息列表
     */
    public List<MetaInfo> searchLocalRepository(String groupId, String artifactId) {
        String localRepoPath = repositoryConfig.getRepositoryDir();
        return SDKUtils.searchLocalRepository(groupId, artifactId, localRepoPath);
    }

    /**
     * 根据精确路径搜索模板
     * 本地未找到时尝试从远程下载
     *
     * @param exactPath 精确路径,格式: groupId/artifactId/version/filePath/filename
     * @return 模板元信息,未找到返回null
     * @throws IOException 文件读取异常
     */
    public LocalMetaInfo searchByPath(String exactPath) throws IOException {
        String localRepoPath = repositoryConfig.getRepositoryDir();

        // 从本地仓库中查找模板
        MetaInfo localResult = SDKUtils.searchByPath(exactPath, localRepoPath);
        if (localResult != null) {
            log.info("本地仓库命中: {}", exactPath);
            LocalMetaInfo result = MetaInfoConvertUtil.convert(localResult);
            result.setTemplateContent(readTemplateContent(localResult));
            return result;
        }

        // 本地未找到,尝试智能下载
        try {
            // 解析路径获取artifactId(格式: groupId/artifactId/version/filePath/filename)
            String[] parts = exactPath.split("/");
            if (parts.length >= 2) {
                String artifactId = parts[1];

                log.info("从路径解析出 artifactId={}, 尝试智能下载", artifactId);

                // 获取远程配置
                RemoteMetaConfig remoteConfig = fetchRemoteMetaConfig(artifactId);
                if (remoteConfig == null) {
                    log.warn("获取远程配置失败: {}", artifactId);
                    return null;
                }

                // 触发智能下载
                boolean downloadSuccess = smartDownloadTemplate(remoteConfig);

                // 下载成功后重新搜索
                if (downloadSuccess) {
                    log.info("智能下载成功，重新搜索: {}", exactPath);
                    localResult = SDKUtils.searchByPath(exactPath, localRepoPath);
                    if (localResult != null) {
                        log.info("修复后成功找到: {}", exactPath);
                        LocalMetaInfo result = MetaInfoConvertUtil.convert(localResult);
                        result.setTemplateContent(readTemplateContent(localResult));
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.error("路径解析或智能下载失败: {}", e.getMessage(), e);
        }

        log.warn("本地仓库未找到且无法修复: {}", exactPath);
        return null;
    }

    /**
     * 智能下载或更新模板
     * 根据SHA256哈希值判断是否需要更新
     *
     * @param remoteConfig 远程模板配置
     * @return 是否成功
     */
    public boolean smartDownloadTemplate(RemoteMetaConfig remoteConfig) {
        String localRepoPath = repositoryConfig.getRepositoryDir();
        String remoteBaseUrl = repositoryConfig.getRemotePath();
        return SDKUtils.smartDownloadTemplate(localRepoPath, remoteBaseUrl, remoteConfig);
    }

    /**
     * 从远程仓库获取元配置
     *
     * @param templateKeyword 模板关键词
     * @return 远程模板配置
     */
    public RemoteMetaConfig fetchRemoteMetaConfig(String templateKeyword) {
        String remoteBaseUrl = repositoryConfig.getRemotePath();
        return SDKUtils.fetchRemoteMetaConfig(remoteBaseUrl, templateKeyword);
    }

    /**
     * 加载模板文件
     * 读取模板文件内容并填充到元信息中
     *
     * @param metaInfos 模板元信息
     * @return 包含文件内容的本地元信息
     */
    public LocalMetaInfo loadTemplateFile(MetaInfo metaInfos) {
        try {
            // 转换为LocalMetaInfo对象(复制元数据)
            LocalMetaInfo localInfo = MetaInfoConvertUtil.convert(metaInfos);

            // 读取并填充模板文件内容(从本地缓存读取)
            localInfo.setTemplateContent(readTemplateContent(metaInfos));
            return localInfo;
        } catch (IOException e) {
            log.error("读取模板文件失败: {}/{}/{}/{}/{}",
                    metaInfos.getGroupId(), metaInfos.getArtifactId(), metaInfos.getVersion(),
                    metaInfos.getFilePath(), metaInfos.getFilename(), e);
        }
        return null;
    }

    /**
     * 读取模板文件内容
     * 从本地缓存目录读取
     *
     * @param info 模板元信息
     * @return 模板文件内容
     * @throws IOException 文件不存在或读取失败
     */
    private String readTemplateContent(MetaInfo info) throws IOException {
        String localCachePath = repositoryConfig.getRepositoryDir();

        // 拼装模板文件绝对路径(本地缓存根目录 + groupId + artifactId + version + filePath + filename)
        Path templatePath = Paths.get(localCachePath,
                info.getGroupId(),
                info.getArtifactId(),
                info.getVersion(),
                info.getFilePath(),
                info.getFilename())
                .toAbsolutePath()
                .normalize();

        // 校验文件是否存在
        if (!Files.exists(templatePath)) {
            throw new IOException("模板文件不存在: " + templatePath);
        }

        // 读取文件内容(一次性读入,文件通常几十KB以内,性能足够)
        return Files.readString(templatePath, StandardCharsets.UTF_8);
    }

    /**
     * 线程池,用于异步加载模板文件
     */
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("template-loader-" + t.getId());
        return t;
    });

    /**
     * 异步加载模板文件
     *
     * @param metaInfos 模板元信息
     * @return 异步Future对象
     */
    public CompletableFuture<LocalMetaInfo> loadTemplateFileAsync(MetaInfo metaInfos) {
        return CompletableFuture.supplyAsync(() -> loadTemplateFile(metaInfos), pool);
    }
}
