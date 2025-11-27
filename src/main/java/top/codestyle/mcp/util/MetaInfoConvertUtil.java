package top.codestyle.mcp.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.meta.LocalMetaVariable;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.MetaVariable;
import top.codestyle.mcp.model.meta.LocalMetaConfig;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 元信息转换工具类
 * 提供MetaInfo、LocalMetaConfig、RemoteMetaConfig之间的转换
 *
 * @author Kanttha, movclantian
 * @since 2025-10-17
 */
public class MetaInfoConvertUtil {

    /**
     * 转换MetaInfo为LocalMetaInfo
     *
     * @param source 源MetaInfo对象
     * @return 转换后的LocalMetaInfo对象，source为null时返回null
     */
    public static LocalMetaInfo convert(MetaInfo source) {
        if (source == null) {
            return null;
        }
        LocalMetaInfo target = new LocalMetaInfo();

        // 复制基础字段
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setGroupId(source.getGroupId());
        target.setArtifactId(source.getArtifactId());
        target.setFilePath(source.getFilePath());
        target.setDescription(source.getDescription());
        target.setFilename(source.getFilename());
        target.setSha256(source.getSha256());

        // 复制变量列表
        List<MetaVariable> vars = source.getInputVariables();
        if (CollUtil.isNotEmpty(vars)) {
            target.setInputVariables(vars);
        }
        return target;
    }

    /**
     * 解析meta.json文件为模板元信息列表(预留扩展)
     * 
     * @param metaFile meta.json文件
     * @return 模板元信息列表
     * @throws IOException 文件读取异常
     */
    public static List<MetaInfo> parseMetaJson(File metaFile) throws IOException {
        List<MetaInfo> result = new ArrayList<>();

        LocalMetaConfig localConfig = JSONUtil.toBean(FileUtil.readUtf8String(metaFile), LocalMetaConfig.class);

        String groupId = localConfig.getGroupId();
        String artifactId = localConfig.getArtifactId();

        if (localConfig.getConfigs() != null) {
            for (LocalMetaConfig.Config config : localConfig.getConfigs()) {
                String version = config.getVersion();

                if (config.getFiles() != null) {
                    for (LocalMetaConfig.FileInfo fileInfo : config.getFiles()) {
                        MetaInfo metaInfo = new MetaInfo();
                        metaInfo.setGroupId(groupId);
                        metaInfo.setArtifactId(artifactId);
                        metaInfo.setVersion(version);
                        metaInfo.setFilename(fileInfo.getFilename());
                        metaInfo.setFilePath(fileInfo.getFilePath());
                        metaInfo.setDescription(fileInfo.getDescription());
                        metaInfo.setSha256(fileInfo.getSha256());
                        metaInfo.setInputVariables(fileInfo.getInputVariables());

                        String fullPath = groupId + File.separator + artifactId + File.separator + version +
                                fileInfo.getFilePath() + File.separator + fileInfo.getFilename();
                        metaInfo.setPath(fullPath);

                        result.add(metaInfo);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 仅解析 meta.json 中“最新版本”为模板元信息列表
     *
     * @param metaFile meta.json 文件
     * @return 仅包含最新版本文件的模板元信息列表
     * @throws IOException 文件读取异常
     */
    public static List<MetaInfo> parseMetaJsonLatestOnly(File metaFile) throws IOException {
        List<MetaInfo> result = new ArrayList<>();

        LocalMetaConfig localConfig = JSONUtil.toBean(FileUtil.readUtf8String(metaFile), LocalMetaConfig.class);

        String groupId = localConfig.getGroupId();
        String artifactId = localConfig.getArtifactId();

        if (CollUtil.isEmpty(localConfig.getConfigs())) {
            return result;
        }

        LocalMetaConfig.Config latest = localConfig.getConfigs().get(localConfig.getConfigs().size() - 1);
        if (latest == null) {
            return result;
        }

        String version = latest.getVersion();
        if (latest.getFiles() != null) {
            for (LocalMetaConfig.FileInfo fileInfo : latest.getFiles()) {
                MetaInfo metaInfo = new MetaInfo();
                metaInfo.setGroupId(groupId);
                metaInfo.setArtifactId(artifactId);
                metaInfo.setVersion(version);
                metaInfo.setFilename(fileInfo.getFilename());
                metaInfo.setFilePath(fileInfo.getFilePath());
                metaInfo.setDescription(fileInfo.getDescription());
                metaInfo.setSha256(fileInfo.getSha256());
                metaInfo.setInputVariables(fileInfo.getInputVariables());

                String fullPath = groupId + File.separator + artifactId + File.separator + version +
                        fileInfo.getFilePath() + File.separator + fileInfo.getFilename();
                metaInfo.setPath(fullPath);

                result.add(metaInfo);
            }
        }

        return result;
    }

    /**
     * 转换远程配置为本地配置
     *
     * @param remoteConfig 远程配置
     * @return 本地配置
     */
    public static LocalMetaConfig.Config convertRemoteToLocalConfig(RemoteMetaConfig remoteConfig) {
        LocalMetaConfig.Config newConfig = new LocalMetaConfig.Config();
        newConfig.setVersion(remoteConfig.getConfig().getVersion());

        List<LocalMetaConfig.FileInfo> localFiles = new ArrayList<>();
        if (remoteConfig.getConfig().getFiles() != null) {
            for (RemoteMetaConfig.FileInfo remoteFile : remoteConfig.getConfig().getFiles()) {
                LocalMetaConfig.FileInfo localFile = new LocalMetaConfig.FileInfo();
                localFile.setFilePath(remoteFile.getFilePath());
                localFile.setFilename(remoteFile.getFilename());
                localFile.setDescription(remoteFile.getDescription());
                localFile.setSha256(remoteFile.getSha256());
                localFile.setInputVariables(remoteFile.getInputVarivales());
                localFiles.add(localFile);
            }
        }
        newConfig.setFiles(localFiles);
        return newConfig;
    }

    /**
     * 转换MetaVariable为LocalMetaVariable（预留扩展）
     *
     * @param src 源MetaVariable对象
     * @return 转换后的LocalMetaVariable对象
     */
    private static LocalMetaVariable convertVariable(MetaVariable src) {
        if (src == null) {
            return null;
        }
        return new LocalMetaVariable();
    }
}
