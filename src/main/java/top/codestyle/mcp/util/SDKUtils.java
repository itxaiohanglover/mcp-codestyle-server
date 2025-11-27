package top.codestyle.mcp.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.model.meta.LocalMetaConfig;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 模板仓库SDK工具类
 * 提供模板搜索、远程下载、本地管理等功能
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
public class SDKUtils {

    /**
     * 根据groupId和artifactId搜索指定模板组
     *
     * @param groupId          组ID
     * @param artifactId       项目ID
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息列表
     */
    public static List<MetaInfo> searchLocalRepository(String groupId, String artifactId, String templateBasePath) {
        List<MetaInfo> result = new ArrayList<>();
        try {
            templateBasePath = normalizePath(templateBasePath);
            File metaFile = new File(templateBasePath + File.separator + groupId + File.separator + artifactId
                    + File.separator + "meta.json");
            if (!metaFile.exists()) {
                return result;
            }
            List<MetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJsonLatestOnly(metaFile);
            for (MetaInfo metaInfo : metaInfoList) {
                if (isTemplateFileExists(templateBasePath, metaInfo)) {
                    result.add(metaInfo);
                }
            }
        } catch (Exception e) {
            // 搜索失败,返回空结果
        }
        return result;
    }

    /**
     * 根据精确路径搜索模板
     *
     * @param exactPath        精确路径,格式: groupId/artifactId/version/filePath/filename
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息,未找到返回null
     */
    public static MetaInfo searchByPath(String exactPath, String templateBasePath) {
        try {
            // 规范化路径
            templateBasePath = normalizePath(templateBasePath);
            String normalizedExactPath = normalizePath(exactPath);

            // 从路径解析 groupId 和 artifactId，直接定位 meta.json
            // 路径格式: groupId/artifactId/version/filePath/filename
            String[] parts = normalizedExactPath.split(Pattern.quote(File.separator));
            if (parts.length < 3) {
                return null;
            }
            String groupId = parts[0];
            String artifactId = parts[1];

            // 直接定位 meta.json 文件
            File metaFile = new File(templateBasePath + File.separator + groupId + File.separator + artifactId
                    + File.separator + "meta.json");
            if (!metaFile.exists()) {
                return null;
            }

            // 在匹配的 meta.json 中查找模板
            List<MetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJsonLatestOnly(metaFile);
            for (MetaInfo metaInfo : metaInfoList) {
                String fullPath = metaInfo.getGroupId() + File.separator + metaInfo.getArtifactId() + File.separator +
                        metaInfo.getVersion() + metaInfo.getFilePath() + File.separator + metaInfo.getFilename();
                if (normalizePath(fullPath).equals(normalizedExactPath)) {
                    return isTemplateFileExists(templateBasePath, metaInfo) ? metaInfo : null;
                }
            }
        } catch (Exception e) {
            // 搜索失败
        }
        return null;
    }

    /**
     * 从远程仓库获取元配置
     *
     * @param remoteBaseUrl   远程仓库基础URL
     * @param templateKeyword 模板关键词,如: RuoYi, CRUD
     * @return 远程模板配置,失败返回null
     */
    public static RemoteMetaConfig fetchRemoteMetaConfig(String remoteBaseUrl, String templateKeyword) {
        try {
            String responseBody = HttpRequest.get(remoteBaseUrl + "/api/mcp/search")
                    .form("templateKeyword", templateKeyword)
                    .timeout(30000)
                    .header("User-Agent", "MCP-CodeStyle-Server/1.0")
                    .execute()
                    .body();

            return JSONUtil.toBean(responseBody, RemoteMetaConfig.class);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 智能下载或更新模板
     * 通过SHA256哈希值判断是否需要更新,下载ZIP并解压到本地仓库,更新meta.json
     *
     * @param localRepoPath 本地仓库路径
     * @param remoteBaseUrl 远程仓库基础URL
     * @param remoteConfig  远程模板配置
     * @return 是否成功
     */
    public static boolean smartDownloadTemplate(String localRepoPath, String remoteBaseUrl,
            RemoteMetaConfig remoteConfig) {
        try {
            String groupId = remoteConfig.getGroupId();
            String artifactId = remoteConfig.getArtifactId();

            boolean needsUpdate = false;
            try {
                String localMetaPath = localRepoPath + File.separator +
                        groupId + File.separator +
                        artifactId + File.separator +
                        "meta.json";
                File localMetaFile = new File(localMetaPath);

                if (!localMetaFile.exists()) {
                    needsUpdate = true;
                } else {
                    needsUpdate = checkIfNeedsUpdate(localMetaFile, remoteConfig, localRepoPath, groupId, artifactId);
                }
            } catch (Exception e) {
                needsUpdate = true;
            }

            if (needsUpdate) {
                return downloadAndExtractTemplate(localRepoPath, remoteBaseUrl, groupId, artifactId, remoteConfig);
            } else {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证模板文件是否存在
     *
     * @param templateBasePath 模板基础路径
     * @param metaInfo         模板元信息
     * @return 文件是否存在
     */
    private static boolean isTemplateFileExists(String templateBasePath, MetaInfo metaInfo) {
        String normalizedFilePath = StrUtil.removePrefix(normalizePath(metaInfo.getFilePath()), File.separator);
        String versionPath = metaInfo.getVersion();

        String actualFilePath = templateBasePath + File.separator +
                metaInfo.getGroupId() + File.separator +
                metaInfo.getArtifactId() + File.separator +
                versionPath + File.separator +
                normalizedFilePath + File.separator +
                metaInfo.getFilename();
        return new File(actualFilePath).exists();
    }

    /**
     * 检查是否需要更新模板
     *
     * @param localMetaFile 本地meta.json文件
     * @param remoteConfig  远程配置
     * @param localRepoPath 本地仓库路径
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @return 是否需要更新
     */
    private static boolean checkIfNeedsUpdate(File localMetaFile, RemoteMetaConfig remoteConfig,
            String localRepoPath, String groupId, String artifactId) {
        try {
            LocalMetaConfig localConfig = JSONUtil.toBean(FileUtil.readUtf8String(localMetaFile),
                    LocalMetaConfig.class);
            String remoteVersion = remoteConfig.getConfig().getVersion();

            LocalMetaConfig.Config matchedConfig = findMatchedConfig(localConfig, remoteVersion);
            if (matchedConfig == null) {
                return true;
            }

            List<RemoteMetaConfig.FileInfo> remoteFiles = remoteConfig.getConfig().getFiles();
            if (CollUtil.isEmpty(remoteFiles)) {
                return false;
            }

            List<LocalMetaConfig.FileInfo> localFiles = matchedConfig.getFiles();
            String versionPath = remoteVersion;

            for (RemoteMetaConfig.FileInfo remoteFile : remoteFiles) {
                String normalizedFilePath = normalizePath(remoteFile.getFilePath());
                if (normalizedFilePath.startsWith(File.separator)) {
                    normalizedFilePath = normalizedFilePath.substring(1);
                }

                String actualFilePath = localRepoPath + File.separator + groupId + File.separator +
                        artifactId + File.separator + versionPath + File.separator + normalizedFilePath
                        + File.separator + remoteFile.getFilename();

                if (!new File(actualFilePath).exists()) {
                    return true;
                }

                if (isFileShaChanged(localFiles, remoteFile.getFilename(), remoteFile.getFilePath(),
                        StrUtil.emptyToDefault(remoteFile.getSha256(), ""))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 查找匹配的版本配置
     *
     * @param localConfig 本地配置
     * @param version     版本号
     * @return 匹配的配置,未找到返回null
     */
    private static LocalMetaConfig.Config findMatchedConfig(LocalMetaConfig localConfig, String version) {
        if (localConfig.getConfigs() != null) {
            for (LocalMetaConfig.Config config : localConfig.getConfigs()) {
                if (config.getVersion().equals(version)) {
                    return config;
                }
            }
        }
        return null;
    }

    /**
     * 检查文件SHA256是否变化
     *
     * @param localFiles 本地文件列表
     * @param filename   文件名
     * @param filePath   文件路径
     * @param remoteSha  远程SHA256
     * @return SHA是否变化
     */
    private static boolean isFileShaChanged(List<LocalMetaConfig.FileInfo> localFiles,
            String filename, String filePath, String remoteSha) {
        if (localFiles == null) {
            return true;
        }

        for (LocalMetaConfig.FileInfo localFile : localFiles) {
            if (localFile.getFilename().equals(filename) && localFile.getFilePath().equals(filePath)) {
                String localSha = StrUtil.emptyToDefault(localFile.getSha256(), "");
                return !localSha.equals(remoteSha);
            }
        }
        return true;
    }

    /**
     * 下载并解压模板
     *
     * @param localRepoPath 本地仓库路径
     * @param remoteBaseUrl 远程基础URL
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @param remoteConfig  远程配置
     * @return 是否成功
     */
    private static boolean downloadAndExtractTemplate(String localRepoPath, String remoteBaseUrl,
            String groupId, String artifactId,
            RemoteMetaConfig remoteConfig) {
        String templatePath = File.separator + groupId + File.separator + artifactId;
        String templateDir = localRepoPath + File.separator + groupId + File.separator + artifactId;
        File localMetaFile = new File(templateDir, "meta.json");
        String backupContent = null;
        File zipFile = null;

        try {
            // 备份现有meta.json内容，用于后续版本追加
            if (localMetaFile.exists()) {
                backupContent = FileUtil.readUtf8String(localMetaFile);
            }

            HttpResponse response = HttpRequest.get(remoteBaseUrl + "/api/file/load")
                    .form("paths", templatePath)
                    .timeout(60000)
                    .header("User-Agent", "MCP-CodeStyle-Server/1.0")
                    .execute();

            if (!response.isOk()) {
                return false;
            }

            zipFile = FileUtil.createTempFile("template-", ".zip", true);

            IoUtil.copy(response.bodyStream(), FileUtil.getOutputStream(zipFile));

            // 解压到仓库根目录
            if (extractZipFile(zipFile, localRepoPath, templateDir)) {
                updateLocalMetaJson(localRepoPath, groupId, artifactId, remoteConfig, backupContent);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            FileUtil.del(zipFile);
        }
    }

    /**
     * 解压ZIP文件
     *
     * @param zipFile     ZIP文件
     * @param targetPath  目标路径
     * @param templateDir 当前模板目录
     * @return 是否成功
     */
    private static boolean extractZipFile(File zipFile, String targetPath, String templateDir) {
        try {
            File targetDir = FileUtil.mkdir(targetPath);
            ZipUtil.unzip(zipFile, targetDir);
            // 仅删除当前模板目录下的meta.json，避免影响其他模板
            File metaFile = new File(templateDir, "meta.json");
            if (metaFile.exists()) {
                FileUtil.del(metaFile);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 更新本地meta.json文件
     *
     * @param localRepoPath 本地仓库路径
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @param remoteConfig  远程配置
     * @param backupContent 备份的meta.json内容（用于版本追加）
     */
    private static void updateLocalMetaJson(String localRepoPath, String groupId,
            String artifactId, RemoteMetaConfig remoteConfig, String backupContent) {

        String newVersion = remoteConfig.getConfig().getVersion();

        String localMetaPath = localRepoPath + File.separator + groupId + File.separator +
                artifactId + File.separator + "meta.json";

        File localMetaFile = new File(localMetaPath);

        LocalMetaConfig localConfig;

        // 优先使用备份内容，确保版本追加正确
        if (StrUtil.isNotBlank(backupContent)) {
            localConfig = JSONUtil.toBean(backupContent, LocalMetaConfig.class);
        } else if (FileUtil.exist(localMetaFile)) {
            localConfig = JSONUtil.toBean(FileUtil.readUtf8String(localMetaFile), LocalMetaConfig.class);
        } else {
            localConfig = new LocalMetaConfig();
            localConfig.setGroupId(groupId);
            localConfig.setArtifactId(artifactId);
            localConfig.setConfigs(new ArrayList<>());
        }

        List<LocalMetaConfig.Config> configs = localConfig.getConfigs();
        if (CollUtil.isEmpty(configs)) {
            configs = new ArrayList<>();
            localConfig.setConfigs(configs);
        }

        configs.removeIf(config -> config.getVersion().equals(newVersion));

        LocalMetaConfig.Config newConfig = MetaInfoConvertUtil.convertRemoteToLocalConfig(remoteConfig);
        configs.add(newConfig);

        FileUtil.writeUtf8String(JSONUtil.toJsonPrettyStr(localConfig), localMetaFile);
    }

    /**
     * 规范化路径字符串
     * 统一路径分隔符并移除连续分隔符,确保跨平台兼容性
     *
     * @param path 原始路径字符串
     * @return 规范化后的路径字符串
     */
    public static String normalizePath(String path) {
        if (StrUtil.isEmpty(path)) {
            return path;
        }

        // 统一使用系统分隔符
        String normalizedPath = path.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        // 使用 StrUtil 移除连续的分隔符
        String doubleSep = File.separator + File.separator;
        while (StrUtil.contains(normalizedPath, doubleSep)) {
            normalizedPath = normalizedPath.replace(doubleSep, File.separator);
        }

        return normalizedPath;
    }
}
