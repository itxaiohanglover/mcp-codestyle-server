package top.codestyle.mcp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.codestyle.mcp.model.meta.LocalMetaConfig;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 模板仓库SDK工具类
 * 提供模板搜索、远程下载、本地管理等功能
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
public class SDKUtils {

    /**
     * 同义词映射表
     * 支持中英文混合搜索和自然语言匹配
     */
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();

    static {
        // 后端相关
        SYNONYM_MAP.put("controller", Arrays.asList("控制器", "控制层", "restful"));
        SYNONYM_MAP.put("service", Arrays.asList("服务", "服务层", "业务逻辑", "业务层"));
        SYNONYM_MAP.put("mapper", Arrays.asList("持久层", "数据访问层", "dao"));
        SYNONYM_MAP.put("entity", Arrays.asList("实体", "实体类"));
        SYNONYM_MAP.put("dto", Arrays.asList("数据传输对象", "传输对象"));
        SYNONYM_MAP.put("vo", Arrays.asList("视图对象", "展示对象"));
        SYNONYM_MAP.put("req", Arrays.asList("请求", "请求对象", "request"));
        SYNONYM_MAP.put("resp", Arrays.asList("响应", "响应对象", "response"));
        SYNONYM_MAP.put("query", Arrays.asList("查询", "查询对象", "查询条件"));

        // 前端相关
        SYNONYM_MAP.put("index", Arrays.asList("首页", "主页", "索引页"));
        SYNONYM_MAP.put("list", Arrays.asList("列表页", "清单"));
        SYNONYM_MAP.put("form", Arrays.asList("表单", "表单页", "输入页"));
        SYNONYM_MAP.put("detail", Arrays.asList("详情", "详情页", "明细"));
        SYNONYM_MAP.put("modal", Arrays.asList("弹窗", "对话框", "模态框"));
        SYNONYM_MAP.put("drawer", Arrays.asList("抽屉", "侧边栏"));
        SYNONYM_MAP.put("api", Arrays.asList("接口调用", "前端接口"));

        // 通用概念
        SYNONYM_MAP.put("crud", Arrays.asList("增删改查", "基础操作", "数据操作", "增删查改"));
        SYNONYM_MAP.put("backend", Arrays.asList("后端", "服务端", "server"));
        SYNONYM_MAP.put("frontend", Arrays.asList("前端", "客户端", "ui", "界面"));
        SYNONYM_MAP.put("add", Arrays.asList("新增", "添加", "创建", "create"));
        SYNONYM_MAP.put("edit", Arrays.asList("编辑", "修改", "更新", "update"));
        SYNONYM_MAP.put("delete", Arrays.asList("删除", "移除", "remove"));
        SYNONYM_MAP.put("menu", Arrays.asList("菜单", "导航"));
    }

    /**
     * 根据关键词搜索代码模板
     * 支持多关键词匹配、部分匹配、中英文混合、同义词匹配
     *
     * @param searchText       搜索关键词,支持空格分隔的多关键词
     * @param templateBasePath 模板基础路径
     * @return 匹配的模板元信息列表
     */
    public static List<MetaInfo> searchByKeyword(String searchText, String templateBasePath) {
        List<MetaInfo> result = new ArrayList<>();

        try {
            // 规范化模板基础路径,统一使用系统分隔符
            templateBasePath = normalizePath(templateBasePath);
            File templateDir = new File(templateBasePath);
            if (!templateDir.exists() || !templateDir.isDirectory()) {
                return result;
            }

            // 递归查找所有meta.json文件
            List<File> metaFiles = findMetaJsonFiles(templateDir);

            // 解析并过滤匹配的模板
            for (File metaFile : metaFiles) {
                try {
                    List<MetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJsonLatestOnly(metaFile);
                    for (MetaInfo metaInfo : metaInfoList) {
                        if (isMetaInfoMatched(searchText, metaInfo)
                                && isTemplateFileExists(templateBasePath, metaInfo)) {
                            result.add(metaInfo);
                        }
                    }
                } catch (IOException e) {
                    // 忽略解析失败的文件
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
            // 规范化模板基础路径,统一使用系统分隔符
            templateBasePath = normalizePath(templateBasePath);
            // 统一exactPath分隔符
            String normalizedExactPath = normalizePath(exactPath);
            File templateDir = new File(templateBasePath);
            if (!templateDir.exists() || !templateDir.isDirectory()) {
                return null;
            }

            // 递归查找所有meta.json文件
            List<File> metaFiles = findMetaJsonFiles(templateDir);

            // 在所有模板中查找匹配路径的文件
            for (File metaFile : metaFiles) {
                List<MetaInfo> metaInfoList = MetaInfoConvertUtil.parseMetaJsonLatestOnly(metaFile);
                for (MetaInfo metaInfo : metaInfoList) {
                    // 构建完整路径进行匹配(格式: groupId/artifactId/version/filePath/filename)
                    String fullPath = metaInfo.getGroupId() + File.separator + metaInfo.getArtifactId() + File.separator
                            +
                            metaInfo.getVersion() + metaInfo.getFilePath() + File.separator + metaInfo.getFilename();
                    // 统一fullPath分隔符
                    String normalizedFullPath = normalizePath(fullPath);
                    if (normalizedFullPath.equals(normalizedExactPath)) {
                        return isTemplateFileExists(templateBasePath, metaInfo) ? metaInfo : null;
                    }
                }
            }

        } catch (Exception e) {
            // 搜索失败
        }

        return null;
    }

    /**
     * 发送HTTP GET请求
     *
     * @param baseUrl     基础URL
     * @param apiPath     API路径
     * @param paramName   参数名
     * @param paramValue  参数值
     * @param readTimeout 读取超时时间(毫秒)
     * @return HttpURLConnection对象,失败返回null
     */
    private static HttpURLConnection sendGetRequest(String baseUrl, String apiPath, String paramName, String paramValue,
            int readTimeout) {
        try {
            // 构建完整的URL: baseUrl + apiPath + 查询参数
            String fullUrl = baseUrl + apiPath + "?" + paramName + "="
                    + java.net.URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
            URL requestUrl = new URL(fullUrl);

            // 配置并创建HTTP连接
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("User-Agent", "MCP-CodeStyle-Server/1.0");

            return connection;
        } catch (Exception e) {
            return null;
        }
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
            HttpURLConnection connection = sendGetRequest(remoteBaseUrl, "/api/mcp/search", "templateKeyword",
                    templateKeyword, 30000);
            if (connection == null) {
                return null;
            }

            int responseCode = connection.getResponseCode();

            if (responseCode != 200) {
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(connection.getInputStream(), RemoteMetaConfig.class);

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
     * 递归查找目录下所有meta.json文件
     *
     * @param dir 目录
     * @return meta.json文件列表
     */
    private static List<File> findMetaJsonFiles(File dir) {
        List<File> metaFiles = new ArrayList<>();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return metaFiles;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return metaFiles;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归查找子目录
                metaFiles.addAll(findMetaJsonFiles(file));
            } else if (file.getName().equals("meta.json")) {
                metaFiles.add(file);
            }
        }

        return metaFiles;
    }

    /**
     * 判断元信息是否匹配搜索关键词
     *
     * @param searchText 搜索关键词
     * @param metaInfo   模板元信息
     * @return 是否匹配
     */
    private static boolean isMetaInfoMatched(String searchText, MetaInfo metaInfo) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return true;
        }

        StringBuilder searchableText = new StringBuilder();
        if (metaInfo.getFilename() != null) {
            searchableText.append(metaInfo.getFilename()).append(" ");
        }
        if (metaInfo.getDescription() != null) {
            searchableText.append(metaInfo.getDescription()).append(" ");
        }
        if (metaInfo.getFilePath() != null) {
            searchableText.append(metaInfo.getFilePath()).append(" ");
        }
        if (metaInfo.getGroupId() != null) {
            searchableText.append(metaInfo.getGroupId()).append(" ");
        }
        if (metaInfo.getArtifactId() != null) {
            searchableText.append(metaInfo.getArtifactId()).append(" ");
        }

        String content = searchableText.toString().toLowerCase();
        String[] keywords = searchText.toLowerCase().split("\\s+");

        for (String keyword : keywords) {
            if (keyword.trim().isEmpty()) {
                continue;
            }

            if (content.contains(keyword)) {
                return true;
            }

            for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
                String mainWord = entry.getKey();
                List<String> synonyms = entry.getValue();

                if (keyword.equals(mainWord) || synonyms.contains(keyword)) {
                    if (content.contains(mainWord)) {
                        return true;
                    }
                    for (String synonym : synonyms) {
                        if (content.contains(synonym)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 验证模板文件是否存在
     *
     * @param templateBasePath 模板基础路径
     * @param metaInfo         模板元信息
     * @return 文件是否存在
     */
    private static boolean isTemplateFileExists(String templateBasePath, MetaInfo metaInfo) {
        String normalizedFilePath = normalizePath(metaInfo.getFilePath());
        if (normalizedFilePath.startsWith(File.separator)) {
            normalizedFilePath = normalizedFilePath.substring(1);
        }

        String versionPath = metaInfo.getVersion();
        if (versionPath.startsWith("v")) {
            versionPath = versionPath.substring(1);
        }

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
            ObjectMapper mapper = new ObjectMapper();
            LocalMetaConfig localConfig = mapper.readValue(localMetaFile, LocalMetaConfig.class);
            String remoteVersion = remoteConfig.getConfig().getVersion();

            LocalMetaConfig.Config matchedConfig = findMatchedConfig(localConfig, remoteVersion);
            if (matchedConfig == null) {
                return true;
            }

            return hasFileChanges(remoteConfig, matchedConfig, localRepoPath, groupId, artifactId);
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
     * 检查文件是否有变化
     *
     * @param remoteConfig  远程配置
     * @param localConfig   本地配置
     * @param localRepoPath 本地仓库路径
     * @param groupId       组ID
     * @param artifactId    项目ID
     * @return 是否有文件变化
     */
    private static boolean hasFileChanges(RemoteMetaConfig remoteConfig, LocalMetaConfig.Config localConfig,
            String localRepoPath, String groupId, String artifactId) {
        List<RemoteMetaConfig.FileInfo> remoteFiles = remoteConfig.getConfig().getFiles();
        List<LocalMetaConfig.FileInfo> localFiles = localConfig.getFiles();
        String version = remoteConfig.getConfig().getVersion();
        String versionPath = version.startsWith("v") ? version.substring(1) : version;

        if (remoteFiles == null || remoteFiles.isEmpty()) {
            return false;
        }

        for (RemoteMetaConfig.FileInfo remoteFile : remoteFiles) {
            String remoteSha = remoteFile.getSha256() != null ? remoteFile.getSha256() : "";
            String remoteFilename = remoteFile.getFilename();
            String remoteFilePath = remoteFile.getFilePath();

            String normalizedFilePath = normalizePath(remoteFilePath);
            if (normalizedFilePath.startsWith(File.separator)) {
                normalizedFilePath = normalizedFilePath.substring(1);
            }

            String actualFilePath = localRepoPath + File.separator + groupId + File.separator +
                    artifactId + File.separator + versionPath + File.separator + normalizedFilePath
                    + File.separator + remoteFilename;

            if (!new File(actualFilePath).exists()) {
                return true;
            }

            if (isFileShaChanged(localFiles, remoteFilename, remoteFilePath, remoteSha)) {
                return true;
            }
        }
        return false;
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
                String localSha = localFile.getSha256() != null ? localFile.getSha256() : "";
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
        File zipFile = null;

        try {
            HttpURLConnection connection = sendGetRequest(remoteBaseUrl, "/api/file/load", "paths", templatePath,
                    60000);
            if (connection == null || connection.getResponseCode() != 200) {
                return false;
            }

            zipFile = File.createTempFile("template-" + templatePath, ".zip");
            zipFile.deleteOnExit();

            try (InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(zipFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            if (extractZipFile(zipFile, localRepoPath)) {
                updateLocalMetaJson(localRepoPath, groupId, artifactId, remoteConfig);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (zipFile != null) {
                zipFile.delete();
            }
        }
    }

    /**
     * 解压ZIP文件
     *
     * @param zipFile    ZIP文件
     * @param targetPath 目标路径
     * @return 是否成功
     */
    private static boolean extractZipFile(File zipFile, String targetPath) {
        try {
            File targetDir = new File(targetPath);
            targetDir.mkdirs();

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory() || entry.getName().endsWith("meta.json")) {
                        continue;
                    }

                    File targetFile = new File(targetDir, entry.getName());
                    targetFile.getParentFile().mkdirs();

                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                }
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
     */
    private static void updateLocalMetaJson(String localRepoPath, String groupId,
            String artifactId, RemoteMetaConfig remoteConfig) throws IOException {
        String newVersion = remoteConfig.getConfig().getVersion();
        String localMetaPath = localRepoPath + File.separator + groupId + File.separator +
                artifactId + File.separator + "meta.json";
        File localMetaFile = new File(localMetaPath);

        ObjectMapper mapper = new ObjectMapper();
        LocalMetaConfig localConfig;

        if (localMetaFile.exists()) {
            localConfig = mapper.readValue(localMetaFile, LocalMetaConfig.class);
        } else {
            localConfig = new LocalMetaConfig();
            localConfig.setGroupId(groupId);
            localConfig.setArtifactId(artifactId);
            localConfig.setConfigs(new ArrayList<>());
        }

        List<LocalMetaConfig.Config> configs = localConfig.getConfigs();
        if (configs == null) {
            configs = new ArrayList<>();
            localConfig.setConfigs(configs);
        }

        configs.removeIf(config -> config.getVersion().equals(newVersion));

        LocalMetaConfig.Config newConfig = MetaInfoConvertUtil.convertRemoteToLocalConfig(remoteConfig);
        configs.add(newConfig);

        localMetaFile.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(localMetaFile, localConfig);
    }

    /**
     * 规范化路径字符串
     * 统一路径分隔符并移除连续分隔符,确保跨平台兼容性
     *
     * @param path 原始路径字符串
     * @return 规范化后的路径字符串
     */
    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // 统一使用系统分隔符
        String normalizedPath = path.replace('/', File.separatorChar).replace('\\', File.separatorChar);

        // 移除连续的分隔符
        while (normalizedPath.contains(File.separator + File.separator)) {
            normalizedPath = normalizedPath.replace(File.separator + File.separator, File.separator);
        }

        return normalizedPath;
    }
}
