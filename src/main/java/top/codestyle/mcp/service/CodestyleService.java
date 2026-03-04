package top.codestyle.mcp.service;

import cn.hutool.json.JSONUtil;
import com.github.javaparser.ParseProblemException;
import lombok.RequiredArgsConstructor;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.model.remote.RemoteSearchResult;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.AstTemplateGenerator;
import top.codestyle.mcp.util.CodestyleClient;
import top.codestyle.mcp.util.PromptUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码模板搜索和内容获取服务
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-12-03
 */
@Service
@RequiredArgsConstructor
public class CodestyleService {

    private final TemplateService templateService;
    private final PromptService promptService;
    private final LuceneIndexService luceneIndexService;
    private final RepositoryConfig repositoryConfig;

    /**
     * 搜索代码模板
     * <p>根据模板提示词搜索模板信息，返回目录树和模板组介绍。
     * 支持本地Lucene检索和远程检索两种模式。
     *
     * @param templateKeyword 模板提示词，支持关键词或 groupId/artifactId 格式，如: CRUD, backend, frontend, continew/DatabaseConfig
     * @return 模板目录树和描述信息字符串
     */
    @McpTool(name = "codestyleSearch", description = """
            根据模板提示词搜索代码模板库，返回匹配的模板目录树和模板组介绍。
            支持以下搜索格式：
            1. 关键词搜索：CRUD, frontend, backend 等
            2. 精确搜索：groupId/artifactId 格式
            """)
    public String codestyleSearch(
            @McpToolParam(description = "模板提示词，如: CRUD, bankend, frontend等") String templateKeyword) {
        try {
            // 远程检索模式
            if (templateService.isRemoteSearchEnabled()) {
                List<RemoteSearchResult> remoteResults = templateService.searchFromRemote(templateKeyword);

                // 远程返回空结果，自动fallback到本地Lucene检索
                if (remoteResults.isEmpty()) {
                    return searchLocalFallback(templateKeyword, true);
                }

                // 单个结果：精确匹配，下载到本地并返回目录树
                if (remoteResults.size() == 1) {
                    RemoteSearchResult result = remoteResults.get(0);
                    
                    // 下载模板
                    templateService.downloadTemplate(result);

                    List<top.codestyle.mcp.model.template.TemplateMetaInfo> metaInfos = templateService.searchLocalRepository(
                            result.getGroupId(), result.getArtifactId());
                    
                    if (metaInfos.isEmpty()) {
                        return "本地仓库模板文件不完整,请检查模板目录";
                    }

                    TreeNode treeNode = PromptUtils.buildTree(metaInfos);
                    String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();
                    
                    // 使用 snippet 作为描述（如果没有则使用 title）
                    String description = result.getSnippet() != null ? result.getSnippet() : result.getTitle();
                    return promptService.buildSearchResult(result.getArtifactId(), treeStr, description);
                }

                // 多个结果：返回列表让AI选择
                return promptService.buildRemoteSearchResultResponse(templateKeyword, remoteResults);
            }

            // 本地Lucene全文检索模式
            return searchLocalFallback(templateKeyword, false);
        } catch (Exception e) {
            return "模板搜索失败: " + e.getMessage();
        }
    }

    /**
     * 本地Lucene检索（兜底策略）
     */
    private String searchLocalFallback(String templateKeyword, boolean fromRemoteFallback) {
        List<LuceneIndexService.SearchResult> searchResults = luceneIndexService.fetchLocalMetaConfig(templateKeyword);

        if (searchResults.isEmpty()) {
            if (fromRemoteFallback) {
                return promptService.buildRemoteUnavailable(templateKeyword);
            }
            return promptService.buildLocalNotFound(repositoryConfig.getRepositoryDir(), templateKeyword);
        }

        // 检查是否为同一groupId的多个模板（命名空间搜索）
        if (templateService.isGroupIdSearch(searchResults)) {
            return templateService.buildGroupAggregatedResult(templateKeyword, searchResults);
        }

        // 处理多个不同模板的情况（让AI选择）
        if (searchResults.size() > 1) {
            return templateService.buildMultiResultResponse(templateKeyword, searchResults);
        }

        // 单模板结果
        LuceneIndexService.SearchResult searchResult = searchResults.get(0);
        List<top.codestyle.mcp.model.template.TemplateMetaInfo> metaInfos = templateService.searchLocalRepository(
                searchResult.groupId(), searchResult.artifactId());

        if (metaInfos.isEmpty()) {
            return "本地仓库模板文件不完整,请检查模板目录";
        }

        TreeNode treeNode = PromptUtils.buildTree(metaInfos);
        String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();
        return promptService.buildSearchResult(searchResult.artifactId(), treeStr, searchResult.description());
    }

    /**
     * 获取模板文件内容
     * <p>根据完整的模板文件路径获取详细内容，包括变量说明和模板代码
     *
     * @param templatePath 完整模板文件路径（包含版本号和.ftl扩展名），如: backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl
     * @return 模板文件的详细信息字符串（包含变量说明和模板内容）
     * @throws IOException 文件读取异常
     */
    @McpTool(name = "getTemplateByPath", description = "传入模板文件路径,获取模板文件的详细内容(包括变量说明和模板代码)")
    public String getTemplateByPath(
            @McpToolParam(description = "模板文件路径,如:backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {

        // 使用精确路径搜索模板
        top.codestyle.mcp.model.template.TemplateContent matchedTemplate = templateService.searchByPath(templatePath);

        // 校验搜索结果
        if (matchedTemplate == null) {
            return String.format("未找到路径为 '%s' 的模板文件,请检查路径是否正确。", templatePath);
        }

        // 构建变量信息
        Map<String, String> vars = new LinkedHashMap<>();
        if (matchedTemplate.getInputVariables() != null && !matchedTemplate.getInputVariables().isEmpty()) {
            for (var variable : matchedTemplate.getInputVariables()) {
                String desc = String.format("%s（示例：%s）[%s]",
                        variable.getVariableComment(),
                        variable.getExample(),
                        variable.getVariableType());
                vars.put(variable.getVariableName(), desc);
            }
        }

        // 使用PromptUtils格式化变量信息
        String varInfo = vars.isEmpty() ? "无变量" : PromptUtils.buildVarString(vars).trim();

        // 使用PromptService模板构建最终输出
        return promptService.buildPrompt(
                templatePath,
                varInfo,
                matchedTemplate.getTemplateContent() != null ? matchedTemplate.getTemplateContent() : "");
    }

    /**
     * 从文件系统上传模板到本地仓库或远程服务器
     * <p>本地模式：复制到本地缓存并重建索引
     * <p>远程模式：复制到本地缓存、上传到远程服务器并重建索引
     *
     * @param sourcePath 文件系统路径，如: E:/templates/CRUD
     * @param groupId 组ID，如: continew
     * @param artifactId 项目ID，如: CRUD
     * @param version 版本号，如: 1.0.0
     * @param overwrite 是否覆盖已存在的版本（可选，默认 false）
     * @return 上传结果信息字符串
     */
    @McpTool(name = "uploadTemplateFromFileSystem", description = """
            从文件系统上传模板到本地仓库或远程服务器。
            本地模式：复制到本地缓存并重建索引。
            远程模式：复制到本地缓存、上传到远程服务器并重建索引。
            """)
    public String uploadTemplateFromFileSystem(
            @McpToolParam(description = "文件系统路径，如: E:/templates/CRUD") 
            String sourcePath,
            @McpToolParam(description = "组ID，如: continew") 
            String groupId,
            @McpToolParam(description = "项目ID，如: CRUD") 
            String artifactId,
            @McpToolParam(description = "版本号，如: 1.0.0") 
            String version,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") 
            Boolean overwrite) {
        try {
            boolean shouldOverwrite = overwrite != null && overwrite;
            
            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：上传到远程服务器
                top.codestyle.mcp.model.local.LocalUploadResult result = 
                    templateService.uploadTemplateFromFileSystemRemote(sourcePath, groupId, artifactId, version, shouldOverwrite);
                
                return String.format("""
                    ✓ 模板已上传
                    - 源路径: %s
                    - 本地路径: %s
                    - 远程 ID: %s
                    - 文件数: %d
                    - 索引已更新
                    """,
                    sourcePath,
                    result.getLocalPath(),
                    result.getRemoteId(),
                    result.getFileCount()
                );
            } else {
                // 本地模式：只保存到本地
                top.codestyle.mcp.model.local.LocalUploadResult result = 
                    templateService.saveTemplateFromFileSystemLocal(sourcePath, groupId, artifactId, version, shouldOverwrite);
                
                return String.format("""
                    ✓ 模板已保存到本地
                    - 源路径: %s
                    - 本地路径: %s
                    - 文件数: %d
                    - 索引已更新
                    """,
                    sourcePath,
                    result.getLocalPath(),
                    result.getFileCount()
                );
            }
        } catch (IllegalArgumentException e) {
            return "✗ 上传失败: " + e.getMessage();
        } catch (IOException e) {
            return "✗ 上传失败: " + e.getMessage();
        } catch (Exception e) {
            return "✗ 上传失败: " + e.getMessage();
        }
    }

    /**
     * 上传模板到本地仓库或远程服务器
     * <p>本地模式：保存到本地缓存并重建索引
     * <p>远程模式：保存到本地缓存、上传到远程服务器并重建索引
     *
     * @param templatePath 模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0
     * @param overwrite 是否覆盖已存在的版本（可选，默认 false）
     * @return 上传结果信息字符串
     */
    @McpTool(name = "uploadTemplate", description = """
            上传模板到本地仓库或远程服务器。
            本地模式：保存到本地缓存并重建索引。
            远程模式：保存到本地缓存、上传到远程服务器并重建索引。
            """)
    public String uploadTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") 
            String templatePath,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") 
            Boolean overwrite) {
        try {
            boolean shouldOverwrite = overwrite != null && overwrite;
            
            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：上传到远程服务器
                top.codestyle.mcp.model.local.LocalUploadResult result = 
                    templateService.uploadTemplateRemote(templatePath, shouldOverwrite);
                
                return String.format("""
                    ✓ 模板已上传
                    - 本地路径: %s
                    - 远程 ID: %s
                    - 文件数: %d
                    - 索引已更新
                    """,
                    result.getLocalPath(),
                    result.getRemoteId(),
                    result.getFileCount()
                );
            } else {
                // 本地模式：只保存到本地
                top.codestyle.mcp.model.local.LocalUploadResult result = 
                    templateService.saveTemplateLocal(templatePath, shouldOverwrite);
                
                return String.format("""
                    ✓ 模板已保存到本地
                    - 路径: %s
                    - 文件数: %d
                    - 索引已更新
                    """,
                    result.getLocalPath(),
                    result.getFileCount()
                );
            }
        } catch (IllegalArgumentException e) {
            return "✗ 上传失败: " + e.getMessage();
        } catch (IOException e) {
            return "✗ 上传失败: " + e.getMessage();
        } catch (Exception e) {
            return "✗ 上传失败: " + e.getMessage();
        }
    }

    /**
     * 传入标准 Java 代码，基于 AST 提取核心变量并生成 .ftl 模板内容。
     *
     * @param rawJavaCode 标准的 Java 源代码字符串
     * @return 包含模板内容和变量清单的 JSON 字符串
     */
    @McpTool(name = "generateTemplateFromCode", description = "传入标准Java代码，基于AST进行静态结构分析，自动提取变量并将其转换为.ftl模板，返回包含模板内容和输入变量清单的JSON，用于辅助模板生成工作流")
    public String generateTemplateFromCode(
            @McpToolParam(description = "标准的Java源代码字符串") String rawJavaCode) {
        if (rawJavaCode == null || rawJavaCode.isBlank()) {
            return "输入代码为空，请提供标准Java源代码后再试";
        }
        try {
            AstTemplateGenerator.GenerateResult result = AstTemplateGenerator.generate(rawJavaCode);
            return JSONUtil.toJsonPrettyStr(result);
        } catch (ParseProblemException e) {
            return "代码语法不完整，AST解析失败，请检查或补充导入语句后再试";
        } catch (Exception e) {
            return "模板生成失败: " + e.getMessage();
        }
    }

    /**
     * 删除指定版本的模板
     * <p>本地模式：删除本地缓存并重建索引
     * <p>远程模式：删除本地缓存、删除远程模板并重建索引
     *
     * @param templatePath 模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0
     * @return 删除结果信息字符串
     */
    @McpTool(name = "deleteTemplate", description = """
            删除指定版本的模板。
            本地模式：删除本地缓存并重建索引。
            远程模式：删除本地缓存、删除远程模板并重建索引。
            """)
    public String deleteTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") 
            String templatePath) {
        try {
            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：删除远程模板
                top.codestyle.mcp.model.local.LocalDeleteResult result = 
                    templateService.deleteTemplateRemote(templatePath);
                
                return String.format("""
                    ✓ 模板已删除
                    - 本地路径: %s/%s/%s
                    - 远程 ID: %s/%s/%s
                    - 索引已更新
                    """,
                    result.getGroupId(), result.getArtifactId(), result.getVersion(),
                    result.getGroupId(), result.getArtifactId(), result.getVersion()
                );
            } else {
                // 本地模式：只删除本地
                top.codestyle.mcp.model.local.LocalDeleteResult result = 
                    templateService.deleteTemplateLocal(templatePath);
                
                return String.format("""
                    ✓ 模板已删除
                    - 路径: %s/%s/%s
                    - 索引已更新
                    """,
                    result.getGroupId(), result.getArtifactId(), result.getVersion()
                );
            }
        } catch (IllegalArgumentException e) {
            return "✗ 删除失败: " + e.getMessage();
        } catch (IOException e) {
            return "✗ 删除失败: " + e.getMessage();
        } catch (Exception e) {
            return "✗ 删除失败: " + e.getMessage();
        }
    }
}
