package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.ast.ProjectSkeleton;
import top.codestyle.mcp.model.local.LocalDeleteResult;
import top.codestyle.mcp.model.local.LocalUploadResult;
import top.codestyle.mcp.model.template.TemplateContent;
import top.codestyle.mcp.model.template.TemplateMetaInfo;
import top.codestyle.mcp.model.remote.RemoteSearchResult;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.PromptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.DependencyGraph;

/**
 * 代码模板 MCP 工具服务
 * <p>
 * 封装面向 AI 大模型的 MCP Tool，提供模板搜索、内容获取、
 * 上传和删除等工具接口，放在 MCP 协议层与具体业务逻辑之间的编排层。
 * <p>
 * v2.1.0 新增：项目骨架提取、智能剪枝、意图分析、沙盒验证等深度研究工具。
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-12-03
 */
@Service
@RequiredArgsConstructor
public class CodestyleService {

    private static final int MCP_MAX_INLINE_CHARS = OutputGuardService.DEFAULT_MAX_INLINE_CHARS;

    private final TemplateService templateService;
    private final PromptService promptService;
    private final LuceneIndexService luceneIndexService;
    private final RepositoryConfig repositoryConfig;
    private final AstParsingService astParsingService;
    private final RepoMasterScoringService scoringService;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final SkeletonFormatterService skeletonFormatterService;
    private final SkeletonCacheService skeletonCacheService;
    private final TemplateWatcherService watcherService;
    private final SandboxVerificationService sandboxService;
    private final OutputGuardService outputGuardService;

    /** P5: 前 2 次 explore 响应附加 hint，后续省略以减少重复 token */
    private final AtomicInteger exploreCallCount = new AtomicInteger(0);

    /**
     * 搜索代码模板
     * <p>
     * 根据模板提示词搜索模板信息，返回目录树和模板组介绍。
     * 支持本地Lucene检索和远程检索两种模式。
     *
     * @param templateKeyword 模板提示词，支持关键词或 groupId/artifactId 格式，如: CRUD, backend,
     *                        frontend, continew/DatabaseConfig
     * @return 模板目录树和描述信息字符串
     */
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
                    boolean downloaded = templateService.downloadTemplate(result);
                    if (!downloaded) {
                        return "远程模板下载失败: " + result.getGroupId() + "/" + result.getArtifactId() + "/"
                                + result.getVersion();
                    }

                    List<TemplateMetaInfo> metaInfos = templateService.searchLocalRepository(
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
     * 本地 Lucene 检索（兜底策略）
     * <p>
     * 当远程检索不可用或返回空结果时，回退到本地全文检索。
     *
     * @param templateKeyword    搜索关键词
     * @param fromRemoteFallback 是否由远程检索降级触发
     * @return 格式化的检索结果字符串
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
        List<TemplateMetaInfo> metaInfos = templateService.searchLocalRepository(
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
     * <p>
     * 根据完整的模板文件路径获取详细内容，包括变量说明和模板代码
     *
     * @param templatePath 完整模板文件路径（包含版本号和.ftl扩展名），如:
     *                     backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl
     * @return 模板文件的详细信息字符串（包含变量说明和模板内容）
     * @throws IOException 文件读取异常
     */
    public String getTemplateByPath(
            @McpToolParam(description = "模板文件路径,如:backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {

        // 使用精确路径搜索模板
        TemplateContent matchedTemplate = templateService.searchByPath(templatePath);

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
     * <p>
     * 本地模式：复制到本地缓存并重建索引
     * <p>
     * 远程模式：复制到本地缓存、上传到远程服务器并重建索引
     *
     * @param sourcePath 文件系统路径，如: E:/templates/CRUD
     * @param groupId    组ID，如: continew
     * @param artifactId 项目ID，如: CRUD
     * @param version    版本号，如: 1.0.0
     * @param overwrite  是否覆盖已存在的版本（可选，默认 false）
     * @return 上传结果信息字符串
     */
    public String uploadTemplateFromFileSystem(
            @McpToolParam(description = "文件系统路径，如: E:/templates/CRUD") String sourcePath,
            @McpToolParam(description = "组ID，如: continew") String groupId,
            @McpToolParam(description = "项目ID，如: CRUD") String artifactId,
            @McpToolParam(description = "版本号，如: 1.0.0") String version,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") Boolean overwrite) {
        try {
            boolean shouldOverwrite = overwrite != null && overwrite;

            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：上传到远程服务器
                LocalUploadResult result = templateService.uploadTemplateFromFileSystemRemote(sourcePath, groupId,
                        artifactId, version, shouldOverwrite);

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
                        result.getFileCount());
            } else {
                // 本地模式：只保存到本地
                LocalUploadResult result = templateService.saveTemplateFromFileSystemLocal(sourcePath, groupId,
                        artifactId, version, shouldOverwrite);

                return String.format("""
                        ✓ 模板已保存到本地
                        - 源路径: %s
                        - 本地路径: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        sourcePath,
                        result.getLocalPath(),
                        result.getFileCount());
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.contains("模板路径格式错误")) {
                message = message + "，请传入仓库相对路径，例如: continew/CRUD/1.0.0（不要包含 codestyle-cache 前缀）";
            }
            return "✗ 上传失败: " + message;
        }
    }

    /**
     * 上传模板到本地仓库或远程服务器
     * <p>
     * 本地模式：保存到本地缓存并重建索引
     * <p>
     * 远程模式：保存到本地缓存、上传到远程服务器并重建索引
     *
     * @param templatePath 模板路径，格式: groupId/artifactId/version，如:
     *                     continew/CRUD/1.0.0
     * @param overwrite    是否覆盖已存在的版本（可选，默认 false）
     * @return 上传结果信息字符串
     */
    public String uploadTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") String templatePath,
            @McpToolParam(description = "是否覆盖已存在的版本（可选，默认 false）") Boolean overwrite) {
        try {
            boolean shouldOverwrite = overwrite != null && overwrite;

            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：上传到远程服务器
                LocalUploadResult result = templateService.uploadTemplateRemote(templatePath, shouldOverwrite);

                return String.format("""
                        ✓ 模板已上传
                        - 本地路径: %s
                        - 远程 ID: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        result.getLocalPath(),
                        result.getRemoteId(),
                        result.getFileCount());
            } else {
                // 本地模式：只保存到本地
                LocalUploadResult result = templateService.saveTemplateLocal(templatePath, shouldOverwrite);

                return String.format("""
                        ✓ 模板已保存到本地
                        - 路径: %s
                        - 文件数: %d
                        - 索引已更新
                        """,
                        result.getLocalPath(),
                        result.getFileCount());
            }
        } catch (Exception e) {
            return "✗ 上传失败: " + e.getMessage();
        }
    }

    /**
     * 删除指定版本的模板
     * <p>
     * 本地模式：删除本地缓存并重建索引
     * <p>
     * 远程模式：删除本地缓存、删除远程模板并重建索引
     *
     * @param templatePath 模板路径，格式: groupId/artifactId/version，如:
     *                     continew/CRUD/1.0.0
     * @return 删除结果信息字符串
     */
    public String deleteTemplate(
            @McpToolParam(description = "模板路径，格式: groupId/artifactId/version，如: continew/CRUD/1.0.0") String templatePath) {
        try {
            // 判断是否启用远程模式
            if (templateService.isRemoteSearchEnabled()) {
                // 远程模式：删除远程模板
                LocalDeleteResult result = templateService.deleteTemplateRemote(templatePath);

                return String.format("""
                        ✓ 模板已删除
                        - 本地路径: %s/%s/%s
                        - 远程 ID: %s/%s/%s
                        - 索引已更新
                        """,
                        result.getGroupId(), result.getArtifactId(), result.getVersion(),
                        result.getGroupId(), result.getArtifactId(), result.getVersion());
            } else {
                // 本地模式：只删除本地
                LocalDeleteResult result = templateService.deleteTemplateLocal(templatePath);

                return String.format("""
                        ✓ 模板已删除
                        - 路径: %s/%s/%s
                        - 索引已更新
                        """,
                        result.getGroupId(), result.getArtifactId(), result.getVersion());
            }
        } catch (Exception e) {
            return "✗ 删除失败: " + e.getMessage();
        }
    }

    // ========================= 深度研究工具 (v2.1.0) =========================

    /**
     * 提取项目骨架
     * <p>
     * 对指定目录执行多语言 AST 深度解析，构建层级代码树 (HCT) 与
     * 多类型模块依赖图 (MDG)，通过 PageRank + 复合评分智能剪枝，
     * 以 Markdown+XML 混合格式返回精简骨架。
     *
     * @param projectPath  项目目录绝对路径
     * @param detailLevel  详情级别 1–4：1=目录概览，2=类骨架(推荐)，3=方法签名+docstring，4=完整
     * @param focusPath    可选，聚焦子目录相对路径（如 src/main），仅解析该目录
     * @return Markdown+XML 格式的项目骨架
     */
    public String extractProjectSkeleton(
            @McpToolParam(description = "项目目录绝对路径") String projectPath,
            @McpToolParam(required = false, description = "详情级别 1-4: 1=目录概览, 2=类骨架(推荐), 3=方法签名, 4=完整") Integer detailLevel,
            @McpToolParam(required = false, description = "可选: 聚焦的子目录路径(如 src/main)，仅解析该目录") String focusPath,
            @McpToolParam(required = false, description = "强制刷新缓存(默认false)") Boolean forceRefresh) {
        try {
            if (projectPath == null || projectPath.isBlank()) {
                return "✗ 缺少参数 projectPath（项目目录绝对路径）";
            }
            boolean refresh = forceRefresh != null && forceRefresh;
            if (!refresh) {
                SkeletonCacheService.CachedSkeleton existing = skeletonCacheService.get(projectPath);
                if (existing != null && existing.skeleton() != null) {
                    return buildCacheHitSummary(existing, existing.skeleton(), projectPath);
                }
            }
            int level = (detailLevel != null && detailLevel >= 1 && detailLevel <= 4) ? detailLevel : 2;

            // Phase 1: AST 解析（可选 focusPath）
            ProjectSkeleton skeleton = astParsingService.parseProject(projectPath, focusPath);

            // Phase 2: 富依赖图构建
            top.codestyle.mcp.model.ast.DependencyGraph graph = dependencyGraphBuilder.build(skeleton);

            // Phase 3: 智能剪枝（层级化）
            ProjectSkeleton pruned = scoringService.prune(skeleton, level, graph);

            // Phase 4: 取 Top 文件用于标注 ★
            List<RepoMasterScoringService.NodeScore> scores = scoringService.scoreNodes(pruned, graph);
            List<String> topPaths = scores.stream().limit(3).map(RepoMasterScoringService.NodeScore::filePath).toList();

            // 写入缓存供 exploreCodeContext 使用：缓存完整骨架（非 pruned）与 NameIndex
            NameIndex nameIndex = NameIndex.from(graph.getNodeIndex());
            skeletonCacheService.put(projectPath, skeleton, graph, nameIndex);

            // 输出 Markdown+XML
            String formatted = skeletonFormatterService.format(pruned, level, topPaths, MCP_MAX_INLINE_CHARS);
            return outputGuardService.guard(formatted, projectPath, "skeleton", MCP_MAX_INLINE_CHARS);
        } catch (Exception e) {
            return "✗ 项目骨架提取失败: " + e.getMessage();
        }
    }

    /**
     * 基于已解析的骨架与依赖图，按意图检索精准代码上下文。
     * 需先对该项目调用 extractProjectSkeleton 以填充缓存。
     *
     * @param projectPath 项目目录绝对路径
     * @param mode        expand / trace / search
     * @param query       文件路径、类名、方法名或搜索关键词
     * @param direction   trace 模式方向: upstream / downstream / both
     * @param maxDepth    最大遍历深度（默认 3）
     * @return 格式化后的上下文或错误提示
     */
    public String exploreCodeContext(
            @McpToolParam(description = "项目目录绝对路径（需先调用 extractProjectSkeleton）") String projectPath,
            @McpToolParam(description = "模式: expand / trace / search") String mode,
            @McpToolParam(description = "查询目标: 文件路径、类名、方法名或搜索关键词。expand 支持 | 分隔批量，如 'file1.py:100-200|file2.py:300-400'") String query,
            @McpToolParam(required = false, description = "trace 模式方向: upstream / downstream / both (默认 both)") String direction,
            @McpToolParam(required = false, description = "最大遍历深度 (默认 3)") Integer maxDepth,
            @McpToolParam(required = false, description = "expand 模式可选: 行范围如 '100-200'（1基，含端点）") String lineRange) {
        try {
            if (projectPath == null || projectPath.isBlank()) {
                return "✗ 缺少参数 projectPath（项目目录绝对路径）";
            }
            SkeletonCacheService.CachedSkeleton cached = skeletonCacheService.get(projectPath);
            if (cached == null) {
                cached = ensureSkeletonCached(projectPath, null);
                if (cached == null) {
                    return "✗ 项目骨架自动构建失败，请检查 projectPath";
                }
            }
            String m = (mode != null && !mode.isBlank()) ? mode.strip().toLowerCase() : "search";
            int depth = (maxDepth != null && maxDepth > 0) ? Math.min(maxDepth, 20) : 3;
            String dir = (direction != null && !direction.isBlank()) ? direction.strip().toLowerCase() : "both";

            String result = switch (m) {
                case "expand" -> runExpand(cached, projectPath, query, lineRange);
                case "trace" -> runTrace(cached, query, dir, depth);
                case "search" -> runSearch(cached, projectPath, query);
                default -> "✗ 未知模式: " + mode + "，支持: expand, trace, search";
            };
            int callNum = exploreCallCount.incrementAndGet();
            if (result != null && !result.startsWith("✗") && callNum <= 2) {
                result = appendNextStepHint(result, m, query);
            }
            if (result != null && !result.startsWith("✗")) {
                int hitCount = estimateHitCount(result, m);
                skeletonCacheService.recordExplore(projectPath, m, query, hitCount);
            }
            int maxChars = switch (m) {
                case "search" -> 4_000;
                case "expand" -> 12_000;
                case "trace" -> 6_000;
                default -> MCP_MAX_INLINE_CHARS;
            };
            return outputGuardService.guard(result, projectPath, "explore-" + m, maxChars);
        } catch (Exception e) {
            return "✗ exploreCodeContext 失败: " + e.getMessage();
        }
    }

    private static int estimateHitCount(String result, String mode) {
        if (result == null || result.isBlank()) return 0;
        if ("search".equals(mode)) {
            int idx = result.indexOf(" hits)");
            if (idx > 0) {
                int start = result.lastIndexOf(" ", idx - 1);
                if (start >= 0 && start < idx - 1) {
                    try {
                        return Integer.parseInt(result.substring(start + 1, idx).trim());
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        if ("trace".equals(mode)) {
            int lineCount = 0;
            for (int i = 0; i < result.length(); i++) {
                if (result.charAt(i) == '\n') lineCount++;
            }
            return Math.max(0, lineCount - 2);
        }
        if ("expand".equals(mode)) return 1;
        return 0;
    }

    private static String appendNextStepHint(String result, String mode, String query) {
        if (result == null || result.isBlank()) return result;
        String hint;
        switch (mode) {
            case "search" -> hint = "[提示] expand(\"类名或文件路径\") 查看完整代码; trace(\"类名\") 追踪依赖链";
            case "expand" -> {
                if (result.contains("lineRange") || result.contains("若要查看特定实现")) {
                    hint = "[提示] 大文件可用 lineRange=\"起-止\" 查看特定段; search(\"关键词\") 搜索该文件";
                } else {
                    hint = "[提示] trace(\"类名\") 查看谁调用了它; search(\"方法名\") 搜索引用";
                }
            }
            case "trace" -> hint = "[提示] expand(\"节点名\") 查看代码实现";
            default -> hint = null;
        }
        if (hint == null) return result;
        return result + "\n\n" + hint;
    }

    private String runExpand(SkeletonCacheService.CachedSkeleton cached, String projectPath, String query, String lineRange) {
        if (query == null || query.isBlank()) return "✗ expand 模式需要提供 query（文件路径或类名）。";
        String q = query.strip();
        if (q.contains("|")) {
            return runBatchExpand(cached, projectPath, q);
        }
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();

        List<AstNode> fileNodes = cached.skeleton() != null && cached.skeleton().getFileNodes() != null
                ? cached.skeleton().getFileNodes()
                : List.of();

        // 1) 优先：按文件路径匹配
        for (AstNode f : fileNodes) {
            String relPath = f.getFilePath();
            if (relPath == null) continue;
            if (matchesFile(relPath, q)) {
                return expandFile(root, f, relPath, lineRange);
            }
        }

        // 2) 其次：基于依赖图 nodeIndex 的 globalNameDict 做名称匹配（类/方法/文件）
        Map<String, AstNode> nodeIndex = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(nodeIndex);
        List<String> candidateIds = nameIndex.lookup(q);
        if (!candidateIds.isEmpty()) {
            String picked = pickBestCandidateId(q, candidateIds);
            if (picked != null) {
                String expanded = expandByNodeId(root, nodeIndex, picked, lineRange);
                if (expanded != null) return expanded;
            }
            return renderAmbiguousExpandCandidates(q, candidateIds, nodeIndex);
        }

        // 3) 兼容路径：回退到骨架 children 精确匹配（当缓存中仍保留了类子节点时）
        for (AstNode f : fileNodes) {
            if (f.getChildren() == null) continue;
            String relPath = f.getFilePath();
            if (relPath == null) continue;
            for (AstNode c : f.getChildren()) {
                if (!isTypeNode(c)) continue;
                if (c.getName() != null && c.getName().equalsIgnoreCase(q)) {
                    return expandTypeInFile(root, f, relPath, c);
                }
                if (c.getChildren() != null) {
                    for (AstNode m : c.getChildren()) {
                        if (!"method".equals(m.getNodeType())) continue;
                        if (m.getName() != null && m.getName().equalsIgnoreCase(q)) {
                            return expandMethodInFile(root, f, relPath, m);
                        }
                    }
                }
            }
        }

        return "✗ 未找到匹配的文件或类: " + query;
    }

    /** P3: 批量 expand，query 格式为 "file1:start-end|file2:start-end|..." 或 "file1|file2"，减少 LLM 轮次 */
    private String runBatchExpand(SkeletonCacheService.CachedSkeleton cached, String projectPath, String multiQuery) {
        String[] targets = multiQuery.split("\\|");
        if (targets.length == 0) return "✗ 批量 expand 需要至少一个目标，用 | 分隔。";
        int perTargetBudget = Math.max(500, EXPAND_BATCH_MAX_CHARS / targets.length);
        StringBuilder sb = new StringBuilder();
        sb.append("## batch expand (").append(targets.length).append(" targets)\n\n");
        for (String t : targets) {
            String part = t != null ? t.strip() : "";
            if (part.isEmpty()) continue;
            String filePart = part;
            String lrPart = null;
            if (part.matches(".*:\\d+-\\d+$")) {
                int idx = part.lastIndexOf(':');
                filePart = part.substring(0, idx).trim();
                lrPart = part.substring(idx + 1).trim();
            }
            String result = runExpand(cached, projectPath, filePart, lrPart);
            if (result != null && !result.startsWith("✗")) {
                if (result.length() > perTargetBudget) {
                    result = result.substring(0, perTargetBudget) + "\n... [截断]";
                }
                sb.append(result).append("\n---\n");
            } else {
                sb.append(part).append(": ").append(result != null ? result : "失败").append("\n---\n");
            }
        }
        return sb.toString().trim();
    }

    private static String pickBestCandidateId(String q, List<String> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) return null;
        String nq = q.strip();
        String qLower = nq.toLowerCase();

        boolean looksLikePath = nq.contains("/") || nq.contains("\\") || qLower.endsWith(".java") || qLower.endsWith(".py")
                || qLower.endsWith(".ts") || qLower.endsWith(".tsx") || qLower.endsWith(".js") || qLower.endsWith(".go");
        boolean looksLikeMethod = !looksLikePath && nq.contains(".");

        if (looksLikePath) {
            for (String id : candidateIds) if (id != null && id.startsWith("file:")) return id;
        }
        if (looksLikeMethod) {
            for (String id : candidateIds) if (id != null && id.startsWith("method:")) return id;
        }
        for (String id : candidateIds) if (id != null && id.startsWith("class:")) return id;
        for (String id : candidateIds) if (id != null && id.startsWith("method:")) return id;
        for (String id : candidateIds) if (id != null && id.startsWith("file:")) return id;
        return candidateIds.get(0);
    }

    private String expandByNodeId(Path root, Map<String, AstNode> nodeIndex, String nodeId, String lineRange) {
        if (nodeId == null || nodeId.isBlank()) return null;
        AstNode node = nodeIndex.get(nodeId);
        if (node == null) return null;

        if (nodeId.startsWith("file:")) {
            String relPath = node.getFilePath();
            if (relPath == null || relPath.isBlank()) {
                relPath = nodeId.substring("file:".length());
            }
            AstNode fileNode = node;
            return expandFile(root, fileNode, relPath, lineRange);
        }

        if (nodeId.startsWith("class:")) {
            String relPath = node.getFilePath();
            if (relPath == null || relPath.isBlank()) {
                // class:<filePath>:<ClassName>
                String body = nodeId.substring("class:".length());
                int idx = body.lastIndexOf(':');
                relPath = idx > 0 ? body.substring(0, idx) : body;
            }
            AstNode fileNode = nodeIndex.get("file:" + relPath);
            if (fileNode == null) {
                // 兜底：构造一个最小 fileNode 供渲染骨架
                fileNode = AstNode.builder().nodeType("file").filePath(relPath).children(List.of()).build();
            }
            return expandTypeInFile(root, fileNode, relPath, node);
        }

        if (nodeId.startsWith("method:")) {
            String relPath = node.getFilePath();
            if (relPath == null || relPath.isBlank()) {
                // method:<filePath>:<...>
                String body = nodeId.substring("method:".length());
                int idx = body.indexOf(':');
                relPath = idx > 0 ? body.substring(0, idx) : body;
            }
            AstNode fileNode = nodeIndex.get("file:" + relPath);
            if (fileNode == null) {
                fileNode = AstNode.builder().nodeType("file").filePath(relPath).children(List.of()).build();
            }
            return expandMethodInFile(root, fileNode, relPath, node);
        }

        return null;
    }

    private String renderAmbiguousExpandCandidates(String query, List<String> candidateIds, Map<String, AstNode> nodeIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("✗ query 命中多个候选实体，请更精确地指定：\n");
        sb.append("- 建议：传完整文件路径（如 `lightrag/kg/postgres_impl.py`），或先 `mode=search` 缩小范围。\n\n");
        int limit = Math.min(12, candidateIds.size());
        for (int i = 0; i < limit; i++) {
            String id = candidateIds.get(i);
            AstNode n = nodeIndex.get(id);
            String name = n != null ? n.getName() : null;
            String fp = n != null ? n.getFilePath() : null;
            int line = n != null ? (n.getStartLine() + 1) : -1;
            sb.append("  - ").append(id);
            if (name != null && !name.isBlank()) sb.append(" | ").append(name);
            if (fp != null && !fp.isBlank()) sb.append(" @ ").append(fp);
            if (line > 0) sb.append(":").append(line);
            sb.append("\n");
        }
        if (candidateIds.size() > limit) {
            sb.append("  ... 还有 ").append(candidateIds.size() - limit).append(" 个候选\n");
        }
        return sb.toString().trim();
    }

    private String expandFile(Path root, AstNode fileNode, String relPath, String lineRange) {
        Path absPath = resolveProjectFile(root, relPath);
        if (absPath == null) return "✗ 路径不安全或无法解析: " + relPath;
        String content;
        try {
            content = Files.readString(absPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "✗ 读取文件失败: " + relPath + " - " + e.getMessage();
        }
        String[] lines = splitLinesPreserve(content);

        if (lineRange != null && !lineRange.isBlank()) {
            LineRange r = parseLineRange(lineRange, lines.length);
            if (r == null) {
                return "✗ lineRange 格式错误: " + lineRange + "（期望如 '100-200'，1基，含端点）";
            }
            String slice = renderSlice(lines, r.startIdxInclusive, r.endIdxInclusive);
            String pathSlash = relPath.replace("\\", "/");
            return "## " + pathSlash + " L" + lineRange.strip() + "\n" + slice;
        }

        // 小文件：直接返回全文
        if (lines.length <= 200) {
            String pathSlash = relPath.replace("\\", "/");
            return "## " + pathSlash + "\n" + content;
        }

        // 大文件：返回文件骨架 + 头尾预览（带行号），引导用户用 lineRange 精确提取
        String skeleton = renderFileSkeleton(fileNode);
        String head = renderSliceWithLineNumbers(lines, 0, Math.min(lines.length - 1, 49));
        String tail = renderSliceWithLineNumbers(lines, Math.max(0, lines.length - 20), lines.length - 1);
        String pathSlash = relPath.replace("\\", "/");
        return "## " + pathSlash + " (" + lines.length + " lines)\n"
                + "### File Skeleton (from cached AST)\n"
                + "```text\n" + skeleton + "\n```\n\n"
                + "### Head (1-50)\n"
                + "```text\n" + head + "\n```\n\n"
                + "### Tail (last 20)\n"
                + "```text\n" + tail + "\n```\n\n"
                + "提示：该文件约 " + lines.length + " 行。若要查看特定实现，请用 `lineRange`（例如 \"3000-3400\"）。";
    }

    private String expandTypeInFile(Path root, AstNode fileNode, String relPath, AstNode typeNode) {
        Path absPath = resolveProjectFile(root, relPath);
        if (absPath == null) return "✗ 路径不安全或无法解析: " + relPath;
        String content;
        try {
            content = Files.readString(absPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "✗ 读取文件失败: " + relPath + " - " + e.getMessage();
        }
        String[] lines = splitLinesPreserve(content);

        int start = clamp(typeNode.getStartLine(), 0, Math.max(0, lines.length - 1));
        int end = clamp(typeNode.getEndLine(), start, Math.max(0, lines.length - 1));

        String slice = renderSliceWithLineNumbers(lines, start, end);
        int classCount = countTypesInFile(fileNode);
        String pathSlash = relPath.replace("\\", "/");
        return "## " + pathSlash + " L" + (start + 1) + "-" + (end + 1) + "\n"
                + "(文件共 " + lines.length + " 行, 含 " + classCount + " 个类/接口, 当前展开 " + typeNode.getName() + ")\n\n"
                + "```text\n" + slice + "\n```";
    }

    private String expandMethodInFile(Path root, AstNode fileNode, String relPath, AstNode methodNode) {
        Path absPath = resolveProjectFile(root, relPath);
        if (absPath == null) return "✗ 路径不安全或无法解析: " + relPath;
        String content;
        try {
            content = Files.readString(absPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "✗ 读取文件失败: " + relPath + " - " + e.getMessage();
        }
        String[] lines = splitLinesPreserve(content);

        int start = clamp(methodNode.getStartLine(), 0, Math.max(0, lines.length - 1));
        int end = clamp(methodNode.getEndLine(), start, Math.max(0, lines.length - 1));

        String slice = renderSliceWithLineNumbers(lines, start, end);
        int classCount = countTypesInFile(fileNode);
        String pathSlash = relPath.replace("\\", "/");
        return "## " + pathSlash + " L" + (start + 1) + "-" + (end + 1) + "\n"
                + "(文件共 " + lines.length + " 行, 含 " + classCount + " 个类/接口, 当前展开方法 " + methodNode.getName() + ")\n\n"
                + "```text\n" + slice + "\n```";
    }

    private static int countTypesInFile(AstNode fileNode) {
        if (fileNode == null || fileNode.getChildren() == null) return 0;
        int n = 0;
        for (AstNode c : fileNode.getChildren()) {
            String t = c.getNodeType();
            if ("class".equals(t) || "interface".equals(t) || "enum".equals(t)) n++;
        }
        return n;
    }

    private boolean matchesFile(String relPath, String q) {
        if (relPath.equals(q)) return true;
        String norm = relPath.replace("\\", "/");
        String nq = q.replace("\\", "/");
        return norm.endsWith("/" + nq) || norm.endsWith(nq);
    }

    private boolean isTypeNode(AstNode n) {
        if (n == null) return false;
        String t = n.getNodeType();
        return "class".equals(t) || "interface".equals(t) || "enum".equals(t);
    }

    private Path resolveProjectFile(Path root, String relPath) {
        // 防止 path traversal：只允许 root 下的规范化路径
        String rp = relPath.replace("\\", "/");
        while (rp.startsWith("/")) rp = rp.substring(1);
        Path p = root.resolve(rp.replace("/", root.getFileSystem().getSeparator())).normalize();
        if (!p.startsWith(root)) return null;
        return p;
    }

    private String renderFileSkeleton(AstNode fileNode) {
        if (fileNode == null || fileNode.getChildren() == null || fileNode.getChildren().isEmpty()) return "(no AST children)";
        StringBuilder sb = new StringBuilder();
        for (AstNode c : fileNode.getChildren()) {
            String nt = c.getNodeType();
            if ("import".equals(nt) || "import_summary".equals(nt) || "package".equals(nt)) continue;
            if (isTypeNode(c)) {
                sb.append(c.getSignature() != null ? c.getSignature() : (nt + " " + c.getName())).append("\n");
                if (c.getChildren() != null) {
                    for (AstNode m : c.getChildren()) {
                        if ("method".equals(m.getNodeType())) {
                            sb.append("  - ").append(m.getSignature() != null ? m.getSignature() : m.getName()).append("\n");
                        } else if ("field".equals(m.getNodeType())) {
                            sb.append("  - ").append(m.getSignature() != null ? m.getSignature() : m.getName()).append("\n");
                        }
                    }
                }
                sb.append("\n");
            } else if ("method".equals(nt)) {
                sb.append(c.getSignature() != null ? c.getSignature() : c.getName()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String[] splitLinesPreserve(String content) {
        // 保持与 startLine/endLine 的“行”语义一致：按 \n 分割
        return content.split("\n", -1);
    }

    private static String renderSlice(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end && i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < end) sb.append("\n");
        }
        return sb.toString();
    }

    private static String renderSliceWithLineNumbers(String[] lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        int s = Math.max(0, start);
        int e = Math.min(lines.length - 1, end);
        for (int i = s; i <= e; i++) {
            sb.append(String.format("%5d|", i + 1)).append(lines[i]).append("\n");
        }
        return sb.toString().replaceAll("\n$", "");
    }

    private record LineRange(int startIdxInclusive, int endIdxInclusive) {}

    private static LineRange parseLineRange(String lineRange, int totalLines) {
        String s = lineRange.strip();
        int dash = s.indexOf('-');
        if (dash <= 0 || dash >= s.length() - 1) return null;
        try {
            int start1 = Integer.parseInt(s.substring(0, dash).trim());
            int end1 = Integer.parseInt(s.substring(dash + 1).trim());
            if (start1 <= 0 || end1 <= 0) return null;
            int a = Math.min(start1, end1);
            int b = Math.max(start1, end1);
            int startIdx = clamp(a - 1, 0, Math.max(0, totalLines - 1));
            int endIdx = clamp(b - 1, startIdx, Math.max(0, totalLines - 1));
            return new LineRange(startIdx, endIdx);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    private static String escapeXmlAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** P3+P4: 确保骨架已缓存，不返回格式化字符串。供 runAnalyze 静默使用。 */
    public SkeletonCacheService.CachedSkeleton ensureSkeletonCached(String projectPath, String focusPath) {
        if (projectPath == null || projectPath.isBlank()) return null;
        SkeletonCacheService.CachedSkeleton existing = skeletonCacheService.get(projectPath);
        if (existing != null && existing.skeleton() != null) return existing;
        try {
            ProjectSkeleton skeleton = astParsingService.parseProject(projectPath, focusPath);
            DependencyGraph graph = dependencyGraphBuilder.build(skeleton);
            NameIndex nameIndex = NameIndex.from(graph.getNodeIndex());
            skeletonCacheService.put(projectPath, skeleton, graph, nameIndex);
            return skeletonCacheService.get(projectPath);
        } catch (Exception e) {
            return null;
        }
    }

    /** P3: 单次搜索收集 nodeIds 与 grepHits，供 runAnalyze 使用。 */
    private record SearchCollectResult(List<String> nodeIds, List<GrepHit> grepHits) {}

    private SearchCollectResult runSingleSearchCollecting(SkeletonCacheService.CachedSkeleton cached, String projectPath, String qRaw) {
        Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        String qLower = qRaw.toLowerCase();
        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(index);

        List<String> allNodeIds = new ArrayList<>();
        List<GrepHit> allGrepHits = new ArrayList<>();

        List<String> strictIds = nameIndex.lookupStrict(qRaw);
        if (!strictIds.isEmpty()) {
            Path root = (projectPath != null && !projectPath.isBlank())
                    ? Paths.get(projectPath).toAbsolutePath().normalize()
                    : Paths.get(".").toAbsolutePath().normalize();
            List<String> fileRelPaths = new ArrayList<>();
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                if (e.getKey() != null && e.getKey().startsWith("file:")) {
                    AstNode n = e.getValue();
                    String p = n != null ? n.getFilePath() : e.getKey().substring("file:".length());
                    if (p != null && !p.isBlank()) fileRelPaths.add(p);
                }
            }
            List<GrepHit> grepHits = grepCodeContent(root, fileRelPaths, qRaw, 10);
            return new SearchCollectResult(strictIds, grepHits);
        }

        List<String> nameIds = nameIndex.lookup(qRaw);
        if (!nameIds.isEmpty()) {
            for (String id : nameIds) {
                if (!allNodeIds.contains(id)) allNodeIds.add(id);
            }
        }
        if (allNodeIds.size() < SEARCH_MERGE_THRESHOLD) {
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                String id = e.getKey();
                AstNode n = e.getValue();
                if (id == null || n == null) continue;
                String nm = n.getName();
                String sig = n.getSignature();
                boolean nameMatch = nm != null && nm.toLowerCase().contains(qLower);
                boolean sigMatch = sig != null && sig.toLowerCase().contains(qLower);
                boolean idMatch = id.toLowerCase().contains(qLower);
                if (!nameMatch && !sigMatch && idMatch && id.startsWith("method:") && id.contains(".")) {
                    String methodNameInId = id.substring(id.lastIndexOf('.') + 1);
                    if (!methodNameInId.toLowerCase().contains(qLower)) {
                        idMatch = false;
                    }
                }
                if (nameMatch || sigMatch || idMatch) {
                    if (!allNodeIds.contains(id)) allNodeIds.add(id);
                    if (allNodeIds.size() >= 60) break;
                }
                if (id.startsWith("method:")) {
                    String suffix = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id.substring(Math.max(id.lastIndexOf(':'), 0) + 1);
                    if (suffix.toLowerCase().contains(qLower) && !allNodeIds.contains(id)) {
                        allNodeIds.add(id);
                        if (allNodeIds.size() >= 60) break;
                    }
                }
            }
        }
        if (allNodeIds.size() < SEARCH_MERGE_THRESHOLD) {
            Path root = (projectPath != null && !projectPath.isBlank())
                    ? Paths.get(projectPath).toAbsolutePath().normalize()
                    : Paths.get(".").toAbsolutePath().normalize();
            List<String> fileRelPaths = new ArrayList<>();
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                String id = e.getKey();
                AstNode n = e.getValue();
                if (id != null && id.startsWith("file:")) {
                    String p = n != null ? n.getFilePath() : id.substring("file:".length());
                    if (p != null && !p.isBlank()) fileRelPaths.add(p);
                }
            }
            List<GrepHit> hits = grepCodeContent(root, fileRelPaths, qRaw, 25);
            allGrepHits.addAll(hits);
        }
        // C1: 当 0 node hits 但 grep 有命中时，从 grep 命中行反查 nodeIndex 提升为节点
        if (allNodeIds.isEmpty() && !allGrepHits.isEmpty()) {
            for (GrepHit h : allGrepHits) {
                String nodeId = findNodeByFileAndLine(index, h.filePath(), h.line());
                if (nodeId != null && !allNodeIds.contains(nodeId)) {
                    allNodeIds.add(nodeId);
                    if (allNodeIds.size() >= 20) break;
                }
            }
        }
        return new SearchCollectResult(allNodeIds, allGrepHits);
    }

    /**
     * C1: 按文件路径与行号在 nodeIndex 中查找包含该行的最小范围节点（method/class/file）。
     */
    private String findNodeByFileAndLine(Map<String, AstNode> index, String filePath, int line) {
        if (index == null || filePath == null || filePath.isBlank() || line < 1) return null;
        String pathNorm = filePath.replace("\\", "/");
        int line0 = line - 1;
        String bestId = null;
        int bestSpan = Integer.MAX_VALUE;
        for (Map.Entry<String, AstNode> e : index.entrySet()) {
            AstNode n = e.getValue();
            if (n == null) continue;
            String fp = n.getFilePath();
            if (fp == null) fp = e.getKey().startsWith("file:") ? e.getKey().substring("file:".length()) : null;
            if (fp == null || !fp.replace("\\", "/").equals(pathNorm)) continue;
            int start = n.getStartLine();
            int end = n.getEndLine();
            if (line0 >= start && line0 <= end) {
                int span = end - start + 1;
                if (span < bestSpan) {
                    bestSpan = span;
                    bestId = e.getKey();
                }
            }
        }
        return bestId;
    }

    private static final int EXPAND_BATCH_MAX_CHARS = 12_000;
    private static final int ANALYZE_REPORT_CHARS_PER_KEYWORD = 1_500;
    private static final int ANALYZE_REPORT_CHARS_CHAIN_BASE = 2_000;
    private static final int ANALYZE_MAX_REPORT_CHARS_CAP = 15_000;
    private static final int ANALYZE_MAX_KEYWORDS = 8;
    private static final int ANALYZE_TOP_EXPAND_PER_KEYWORD = 3;
    private static final int ANALYZE_MAX_EXPAND_TOTAL = 8;
    private static final int ANALYZE_MAX_GREP_PER_KEYWORD = 3;
    private static final int ANALYZE_PREVIEW_LINES = 12;
    private static final int ANALYZE_MAX_CALL_CHAIN_EDGES = 12;

    /**
     * 关键词去重：函数名优先、短名优先。函数名仅做精确去重，类名做子串去重，上限8个。避免 aquery_llm 被 query_llm 子串误删。
     */
    private String[] deduplicateKeywords(String[] raw) {
        if (raw == null || raw.length == 0) return raw;
        List<String> trimmed = new ArrayList<>();
        for (String s : raw) {
            String t = s != null ? s.strip() : "";
            if (t.length() >= 2) trimmed.add(t);
        }
        if (trimmed.isEmpty()) return raw;
        // 函数名优先（含下划线或首字母小写），同类按长度升序
        trimmed.sort((a, b) -> {
            boolean aIsFunc = a.contains("_") || (a.length() > 0 && Character.isLowerCase(a.charAt(0)));
            boolean bIsFunc = b.contains("_") || (b.length() > 0 && Character.isLowerCase(b.charAt(0)));
            if (aIsFunc != bIsFunc) return aIsFunc ? -1 : 1;
            return Integer.compare(a.length(), b.length());
        });
        List<String> out = new ArrayList<>();
        for (String candidate : trimmed) {
            if (out.size() >= ANALYZE_MAX_KEYWORDS) break;
            String cLower = candidate.toLowerCase();
            boolean candidateIsFunc = candidate.contains("_") || (candidate.length() > 0 && Character.isLowerCase(candidate.charAt(0)));
            boolean isDuplicate = false;
            for (String kept : out) {
                String kLower = kept.toLowerCase();
                if (kLower.equals(cLower)) {
                    isDuplicate = true;
                    break;
                }
                // 仅对类名(PascalCase)做子串去重，函数名不子串去重
                if (!candidateIsFunc) {
                    boolean keptIsFunc = kept.contains("_") || (kept.length() > 0 && Character.isLowerCase(kept.charAt(0)));
                    if (!keptIsFunc && (kLower.contains(cLower) || cLower.contains(kLower))) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (!isDuplicate) out.add(candidate);
        }
        return out.toArray(new String[0]);
    }

    /**
     * P3 + B1/B2: 一站式分析 — 关键词去重、预搜索、调用链前置、逐关键词输出，动态报告上限。
     */
    public String runAnalyze(String projectPath, String[] keywords, String focusPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return "✗ 缺少参数 projectPath（项目目录绝对路径）";
        }
        if (keywords == null || keywords.length == 0) {
            return "✗ 缺少参数 keywords（逗号分隔的搜索关键词）";
        }
        String[] deduped = deduplicateKeywords(keywords);
        int reportCharLimit = Math.min(deduped.length * ANALYZE_REPORT_CHARS_PER_KEYWORD + ANALYZE_REPORT_CHARS_CHAIN_BASE, ANALYZE_MAX_REPORT_CHARS_CAP);

        SkeletonCacheService.CachedSkeleton cached = ensureSkeletonCached(projectPath, focusPath);
        if (cached == null || cached.skeleton() == null) {
            return "✗ 项目骨架构建失败，请检查 projectPath 与 focusPath";
        }
        ProjectSkeleton sk = cached.skeleton();
        Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();

        String projectName = projectPath.replace("\\", "/");
        if (projectName.contains("/")) projectName = projectName.substring(projectName.lastIndexOf('/') + 1);

        Set<String> prevKeywords = skeletonCacheService.getAnalyzedKeywords(projectPath);
        boolean isIncremental = prevKeywords != null && !prevKeywords.isEmpty();

        // B2: 预搜索收集所有关键词的 nodeIds 与每关键词结果
        Set<String> allKeywordNodeIds = new LinkedHashSet<>();
        List<SearchCollectResult> resultsPerKeyword = new ArrayList<>();
        List<String> searchedKeywords = new ArrayList<>();
        for (String kw : deduped) {
            String q = kw != null ? kw.strip() : "";
            if (q.length() < 2) continue;
            SearchCollectResult col = runSingleSearchCollecting(cached, projectPath, q);
            resultsPerKeyword.add(col);
            searchedKeywords.add(q);
            allKeywordNodeIds.addAll(col.nodeIds());
        }

        StringBuilder header = new StringBuilder();
        if (isIncremental) {
            // B3: 增量模式不重复输出项目统计，改为精简头
            List<String> newlyAdded = searchedKeywords.stream().filter(k -> !prevKeywords.contains(k)).toList();

            // opt-a: 全命中缓存场景 - 直接返回单行摘要，避免逐关键词输出 [cached summary]
            if (newlyAdded.isEmpty()) {
                skeletonCacheService.recordAnalyzedKeywords(projectPath, searchedKeywords);
                return "## " + projectName + " (incremental)\n"
                        + "All " + searchedKeywords.size() + " keywords already analyzed: "
                        + String.join(", ", searchedKeywords) + "\n"
                        + "Use exploreCodeContext(mode=expand) to read code directly.\n\n"
                        + "---\n"
                        + "Tip: 可用 exploreCodeContext(mode=expand, query=\"file:start-end\") 读取完整代码（支持批量: file1:range|file2:range），减少 Cursor Read。\n";
            }

            header.append("## ").append(projectName).append(" (incremental)\n");
            header.append("Previously analyzed: ").append(String.join(", ", prevKeywords)).append("\n");
            header.append("New in this call: ").append(String.join(", ", newlyAdded)).append("\n\n");
        } else {
            // B1: 首次调用输出项目统计，但去掉无意义的 Files 行
            header.append("## ").append(projectName)
                    .append(" (").append(sk.getTotalFiles()).append(" files, ")
                    .append(sk.getTotalClasses()).append(" classes, ")
                    .append(sk.getTotalMethods()).append(" methods)\n\n");
        }

        // 调用链：优先 AST invokes 边，为空时用 grep 交叉引用推断
        Map<String, Set<String>> callGraphFromGrep = buildCallGraphFromGrep(projectPath, root, index, resultsPerKeyword, searchedKeywords);
        String chainSection = buildCallChainsSection(cached, allKeywordNodeIds);
        if (chainSection == null || chainSection.isBlank()) {
            chainSection = buildChainLinesFromGraph(callGraphFromGrep, searchedKeywords);
        }
        String summarySection = buildSummarySection(callGraphFromGrep, searchedKeywords, resultsPerKeyword, index, chainSection);
        String summaryBlock = (summarySection != null && !summarySection.isBlank())
                ? ("### Summary\n" + summarySection + "\n\n")
                : "";
        String chainBlock = (chainSection != null && !chainSection.isBlank())
                ? ("### Call Flow\n" + chainSection + "\n\n")
                : "";

        // 按调用深度排序关键词（入口在前），便于 LLM 直接理解流程
        List<String> orderedKeywords = topologicalSortKeywords(callGraphFromGrep, searchedKeywords);

        final int headerBudget = 500;
        final int summaryBudget = 800;
        final int chainBudget = 1_200;
        final String footer = "---\n"
                + "Tip: 可用 exploreCodeContext(mode=expand, query=\"file:start-end\") 读取完整代码（支持批量: file1:range|file2:range），减少 Cursor Read。\n";
        final int footerBudget = Math.min(280, footer.length());

        int remaining = reportCharLimit;
        StringBuilder report = new StringBuilder(Math.min(reportCharLimit + 200, ANALYZE_MAX_REPORT_CHARS_CAP + 400));

        String headerBlock = header.toString();
        if (headerBlock.length() > headerBudget) headerBlock = headerBlock.substring(0, headerBudget);
        report.append(headerBlock);
        remaining -= headerBlock.length();

        if (remaining > 0 && !summaryBlock.isBlank()) {
            String s = summaryBlock;
            if (s.length() > summaryBudget) s = s.substring(0, summaryBudget);
            if (s.length() > remaining) s = s.substring(0, remaining);
            report.append(s);
            remaining -= s.length();
        }

        if (remaining > 0 && !chainBlock.isBlank()) {
            String c = chainBlock;
            if (c.length() > chainBudget) c = c.substring(0, chainBudget);
            if (c.length() > remaining) c = c.substring(0, remaining);
            report.append(c);
            remaining -= c.length();
        }

        int kwCount = Math.max(1, orderedKeywords.size());
        int kwTotalBudget = Math.max(0, remaining - footerBudget);
        int perKwBudget = kwTotalBudget / kwCount;

        Set<String> expandedIds = new LinkedHashSet<>();
        for (String q : orderedKeywords) {
            if (remaining <= footerBudget) break;

            int idx = searchedKeywords.indexOf(q);
            if (idx < 0 || idx >= resultsPerKeyword.size()) continue;
            SearchCollectResult col = resultsPerKeyword.get(idx);

            StringBuilder kw = new StringBuilder();
            if (isIncremental && prevKeywords.contains(q)) {
                kw.append("### ").append(q).append(" [cached summary]\n");
                int shown = 0;
                for (String nodeId : col.nodeIds()) {
                    String loc = renderAnalyzeNodeLocator(nodeId, index);
                    if (loc != null && !loc.isBlank()) {
                        kw.append(loc).append("\n");
                        if (++shown >= 2) break;
                    }
                }
                if (shown == 0) {
                    kw.append("(no nodes)\n");
                }
                kw.append("\n");
                String kwStr = kw.toString();
                if (kwStr.length() > remaining - footerBudget) kwStr = kwStr.substring(0, Math.max(0, remaining - footerBudget));
                report.append(kwStr);
                remaining -= kwStr.length();
                continue;
            }

            kw.append("### ").append(q).append(" (").append(col.nodeIds().size()).append(" node hits, ")
                    .append(col.grepHits().size()).append(" grep)\n");

            int taken = 0;
            for (String nodeId : col.nodeIds()) {
                if (taken >= ANALYZE_TOP_EXPAND_PER_KEYWORD) break;
                if (expandedIds.size() >= ANALYZE_MAX_EXPAND_TOTAL) break;
                if (!expandedIds.add(nodeId)) continue;
                String preview = renderAnalyzeNodePreview(projectPath, root, nodeId, index, cached.graph(), ANALYZE_PREVIEW_LINES);
                if (preview != null && !preview.isBlank()) {
                    kw.append(preview).append("\n");
                    taken++;
                }
            }
            int grepShown = 0;
            if (col.nodeIds().isEmpty()) {
                for (GrepHit h : col.grepHits()) {
                    if (grepShown >= ANALYZE_MAX_GREP_PER_KEYWORD) break;
                    // D2: grep 结果增加函数/类上下文
                    String ctx = "";
                    String hitNodeId = findNodeByFileAndLine(index, h.filePath(), h.line());
                    if (hitNodeId != null) {
                        AstNode n = index.get(hitNodeId);
                        if (n != null && n.getName() != null && !n.getName().isBlank())
                            ctx = "in " + n.getName() + " | ";
                    }
                    String snippet = h.snippet().length() > 200 ? h.snippet().substring(0, 197) + "..." : h.snippet();
                    kw.append("[grep] ").append(h.filePath()).append(":").append(h.line())
                            .append(" | ").append(ctx).append(snippet).append("\n");
                    grepShown++;
                }
            }
            kw.append("\n");

            String kwStr = kw.toString();
            int budget = Math.min(perKwBudget, remaining - footerBudget);
            if (budget > 0 && kwStr.length() > budget) {
                String suffix = "\n... [" + q + " 截断, 用 exploreCodeContext(mode=expand) 查看完整代码]\n\n";
                int keep = Math.max(0, budget - suffix.length());
                kwStr = kwStr.substring(0, Math.min(keep, kwStr.length())) + suffix;
            }
            if (kwStr.length() > remaining - footerBudget) kwStr = kwStr.substring(0, Math.max(0, remaining - footerBudget));
            report.append(kwStr);
            remaining -= kwStr.length();
        }

        if (remaining > 0) {
            String f = footer;
            if (f.length() > remaining) f = f.substring(0, remaining);
            report.append(f);
            remaining -= f.length();
        }

        skeletonCacheService.recordAnalyzedKeywords(projectPath, searchedKeywords);

        String out = report.toString().trim();
        if (out.length() > reportCharLimit) {
            out = out.substring(0, reportCharLimit) + "\n\n... [报告已截断至 " + reportCharLimit + " 字符]";
        }
        return out;
    }

    private static String renderAnalyzeNodeLocator(String nodeId, Map<String, AstNode> index) {
        if (nodeId == null || index == null) return null;
        AstNode node = index.get(nodeId);
        if (node == null) return null;
        String kind = nodeId.startsWith("file:") ? "file" : (nodeId.startsWith("class:") ? "class" : (nodeId.startsWith("method:") ? "method" : "node"));
        String name = node.getName() != null ? node.getName() : nodeId;
        String fp = node.getFilePath();
        int start = node.getStartLine() + 1;
        int end = node.getEndLine() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("- [").append(kind).append("] ").append(name);
        if (fp != null && !fp.isBlank() && start > 0) {
            sb.append(" -- ").append(fp.replace("\\", "/")).append(":").append(start);
            if (end > start) sb.append("-").append(end);
        }
        return sb.toString();
    }

    private String renderAnalyzeNodePreview(String projectPath, Path root, String nodeId, Map<String, AstNode> index, DependencyGraph graph, int maxLines) {
        AstNode node = index.get(nodeId);
        if (node == null) return null;
        String kind = nodeId.startsWith("file:") ? "file" : (nodeId.startsWith("class:") ? "class" : (nodeId.startsWith("method:") ? "method" : "node"));
        String name = node.getName() != null ? node.getName() : nodeId;
        String fp = node.getFilePath();
        int line = node.getStartLine() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(kind).append("] ").append(name);
        if (fp != null && !fp.isBlank()) {
            sb.append(" -- ").append(fp.replace("\\", "/"));
            if (line > 0) {
                sb.append(":").append(line);
                int endLine = node.getEndLine() + 1;
                if (endLine > line) {
                    sb.append("-").append(endLine)
                            .append(" (").append(endLine - line + 1).append(" lines)");
                }
            }
        }
        sb.append("\n");
        if ("method".equals(kind) || "class".equals(kind)) {
            String sig = compactOneLine(node.getSignature(), node.getNodeType() + " " + name);
            if (sig != null && !sig.isBlank()) {
                sb.append("  ").append(sig).append("\n");
            }
            String code = extractCodePreview(projectPath, node, maxLines);
            if (code != null && !code.isBlank()) {
                for (String l : code.split("\n")) {
                    sb.append("  ").append(l).append("\n");
                }
            }
        }
        if (graph != null && graph.getGraph() != null && graph.getGraph().containsVertex(nodeId)) {
            List<String> refs = getTopReferencedBy(nodeId, graph, index, 3);
            if (!refs.isEmpty()) {
                sb.append("  ← referenced by: ").append(String.join(", ", refs)).append("\n");
            }
        }
        return sb.toString();
    }

    private static final int ANALYZE_CALL_CHAIN_MAX_DEPTH = 5;

    /**
     * 调用链：仅沿 invokes 边 BFS，生成 "A -> B -> C" 格式。不含 contains/class->method。
     */
    private String buildCallChainsSection(SkeletonCacheService.CachedSkeleton cached, Set<String> nodeIds) {
        DependencyGraph graph = cached.graph();
        if (graph == null || graph.getGraph() == null || nodeIds == null || nodeIds.isEmpty()) return "";
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> g = graph.getGraph();
        Map<String, AstNode> index = graph.getNodeIndex();
        List<String> lines = new ArrayList<>();
        int totalEdges = 0;
        Set<String> seenChains = new HashSet<>();
        for (String start : nodeIds) {
            if (!g.containsVertex(start) || totalEdges >= ANALYZE_MAX_CALL_CHAIN_EDGES) break;
            Deque<String> queue = new ArrayDeque<>();
            Deque<List<String>> pathQueue = new ArrayDeque<>();
            queue.add(start);
            pathQueue.add(new ArrayList<>(List.of(start)));
            Set<String> visited = new HashSet<>();
            visited.add(start);
            while (!queue.isEmpty() && totalEdges < ANALYZE_MAX_CALL_CHAIN_EDGES) {
                String v = queue.poll();
                List<String> path = pathQueue.poll();
                if (path == null || path.size() > ANALYZE_CALL_CHAIN_MAX_DEPTH) continue;
                for (DefaultWeightedEdge e : g.outgoingEdgesOf(v)) {
                    String t = g.getEdgeTarget(e);
                    String et = graph.getEdgeType(v, t);
                    if (!"invokes".equals(et)) continue;
                    if (visited.contains(t)) continue;
                    visited.add(t);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(t);
                    queue.add(t);
                    pathQueue.add(newPath);
                    String chainKey = String.join("->", newPath);
                    if (seenChains.add(chainKey)) {
                        String chainLine = newPath.stream()
                                .map(id -> prettyNodeLabel(id, index))
                                .reduce((a, b) -> a + " -> " + b)
                                .orElse("");
                        if (!chainLine.isBlank()) {
                            lines.add(chainLine);
                            totalEdges++;
                        }
                    }
                }
            }
        }
        if (lines.isEmpty()) {
            // 回退: 仅输出 invokes 直接边
            for (String v : nodeIds) {
                if (!g.containsVertex(v) || totalEdges >= ANALYZE_MAX_CALL_CHAIN_EDGES) break;
                for (DefaultWeightedEdge e : g.outgoingEdgesOf(v)) {
                    if (totalEdges >= ANALYZE_MAX_CALL_CHAIN_EDGES) break;
                    String t = g.getEdgeTarget(e);
                    String et = graph.getEdgeType(v, t);
                    if ("invokes".equals(et)) {
                        lines.add(prettyNodeLabel(v, index) + " -> " + prettyNodeLabel(t, index));
                        totalEdges++;
                    }
                }
            }
        }
        return lines.isEmpty() ? "" : String.join("\n", lines);
    }

    /**
     * 从各关键词的代码预览与 grep 命中中交叉推断调用图：若 A 的代码中出现 B( 或 await B，则 A 调用 B。
     */
    private Map<String, Set<String>> buildCallGraphFromGrep(String projectPath, Path root, Map<String, AstNode> index,
                                                             List<SearchCollectResult> resultsPerKeyword, List<String> searchedKeywords) {
        Map<String, Set<String>> callGraph = new LinkedHashMap<>();
        if (resultsPerKeyword == null || searchedKeywords == null || resultsPerKeyword.size() != searchedKeywords.size()) return callGraph;
        for (int i = 0; i < searchedKeywords.size(); i++) {
            SearchCollectResult col = resultsPerKeyword.get(i);
            String callerCode = collectFullMethodBodyForInference(projectPath, root, index, col);
            if (callerCode == null || callerCode.isBlank()) continue;
            Set<String> callees = new LinkedHashSet<>();
            for (int j = 0; j < searchedKeywords.size(); j++) {
                if (i == j) continue;
                String callee = searchedKeywords.get(j);
                if (callerCode.contains(callee + "(") || callerCode.contains(callee + " (")
                        || callerCode.contains("await " + callee + "(") || callerCode.contains("await " + callee + " (")) {
                    callees.add(callee);
                }
            }
            if (!callees.isEmpty()) callGraph.put(searchedKeywords.get(i), callees);
        }
        return callGraph;
    }

    /**
     * 生成自足摘要：入口函数及位置 + 调用流前几行，便于 LLM 直接引用。
     * Entry = 调用其他关键词最多的关键词（出度最高），而非"没被调用的关键词"。
     * 这样避免 PGGraphStorage（出度=0）被误选为入口，而 aquery_llm（出度=3）才是真正入口。
     */
    private String buildSummarySection(Map<String, Set<String>> callGraph, List<String> searchedKeywords,
                                       List<SearchCollectResult> resultsPerKeyword, Map<String, AstNode> index,
                                       String chainSection) {
        Map<String, Integer> outDeg = new LinkedHashMap<>();
        for (String k : searchedKeywords) outDeg.put(k, 0);
        if (callGraph != null) {
            for (Map.Entry<String, Set<String>> e : callGraph.entrySet()) {
                outDeg.put(e.getKey(), e.getValue().size());
            }
        }
        List<String> callers = searchedKeywords.stream()
                .filter(k -> outDeg.getOrDefault(k, 0) > 0)
                .sorted((a, b) -> {
                    int cmp = Integer.compare(outDeg.getOrDefault(b, 0), outDeg.getOrDefault(a, 0));
                    if (cmp != 0) return cmp;
                    return Integer.compare(searchedKeywords.indexOf(a), searchedKeywords.indexOf(b));
                })
                .toList();
        List<String> entries = callers.isEmpty() ? searchedKeywords : callers;
        if (entries.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int e = 0; e < Math.min(3, entries.size()); e++) {
            String kw = entries.get(e);
            int idx = searchedKeywords.indexOf(kw);
            if (idx < 0 || idx >= resultsPerKeyword.size()) continue;
            SearchCollectResult col = resultsPerKeyword.get(idx);
            if (!col.nodeIds().isEmpty()) {
                AstNode n = index.get(col.nodeIds().get(0));
                if (n != null && n.getFilePath() != null) {
                    String fp = n.getFilePath().replace("\\", "/");
                    int line = n.getStartLine() + 1;
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(kw).append(" (").append(fp).append(":").append(line).append(")");
                }
            }
        }
        if (sb.length() > 0) sb.insert(0, "Entry: ");
        if (chainSection != null && !chainSection.isBlank()) {
            String firstLine = chainSection.contains("\n") ? chainSection.substring(0, chainSection.indexOf('\n')) : chainSection;
            if (firstLine.length() > 120) firstLine = firstLine.substring(0, 117) + "...";
            sb.append("\nFlow: ").append(firstLine);
        }
        return sb.toString();
    }

    /**
     * 按出度降序排列（调用其他关键词最多的在前），而非纯拓扑排序。
     * 互相调用时（aquery_llm ↔ query_llm）不会死锁，出度高的排前面。
     */
    private List<String> topologicalSortKeywords(Map<String, Set<String>> callGraph, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        Map<String, Integer> outDeg = new HashMap<>();
        Map<String, Integer> inDeg = new HashMap<>();
        for (String k : keywords) { outDeg.put(k, 0); inDeg.put(k, 0); }
        if (callGraph != null) {
            for (Map.Entry<String, Set<String>> e : callGraph.entrySet()) {
                outDeg.merge(e.getKey(), e.getValue().size(), Integer::sum);
                for (String callee : e.getValue()) inDeg.merge(callee, 1, Integer::sum);
            }
        }
        List<String> order = new ArrayList<>(keywords);
        order.sort((a, b) -> {
            int netA = outDeg.getOrDefault(a, 0) - inDeg.getOrDefault(a, 0);
            int netB = outDeg.getOrDefault(b, 0) - inDeg.getOrDefault(b, 0);
            if (netA != netB) return Integer.compare(netB, netA);
            int outA = outDeg.getOrDefault(a, 0);
            int outB = outDeg.getOrDefault(b, 0);
            if (outA != outB) return Integer.compare(outB, outA);
            return Integer.compare(keywords.indexOf(a), keywords.indexOf(b));
        });
        return order;
    }

    private String collectCodeTextForResult(SearchCollectResult col, String projectPath, Path root, Map<String, AstNode> index) {
        StringBuilder sb = new StringBuilder();
        int nodeLimit = Math.min(col.nodeIds().size(), ANALYZE_TOP_EXPAND_PER_KEYWORD);
        for (int i = 0; i < nodeLimit; i++) {
            String preview = renderAnalyzeNodePreview(projectPath, root, col.nodeIds().get(i), index, null, ANALYZE_PREVIEW_LINES);
            if (preview != null) sb.append(preview);
        }
        for (GrepHit h : col.grepHits()) {
            if (h.snippet() != null) sb.append(h.snippet()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 专供调用图推断：读取每个 method 节点的完整函数体（startLine 到 endLine），以便检测体内的 callee 调用。
     */
    private String collectFullMethodBodyForInference(String projectPath, Path root, Map<String, AstNode> index, SearchCollectResult col) {
        StringBuilder sb = new StringBuilder();
        for (String nodeId : col.nodeIds()) {
            if (!nodeId.startsWith("method:")) continue;
            AstNode node = index.get(nodeId);
            if (node == null || node.getFilePath() == null) continue;
            Path file = root.resolve(node.getFilePath().replace("\\", "/"));
            if (!Files.isRegularFile(file)) continue;
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                int start = Math.max(0, node.getStartLine());
                int end = Math.min(lines.size() - 1, node.getEndLine());
                for (int i = start; i <= end; i++) {
                    sb.append(lines.get(i)).append("\n");
                }
            } catch (Exception ignored) {
            }
        }
        for (GrepHit h : col.grepHits()) {
            if (h.snippet() != null) sb.append(h.snippet()).append("\n");
        }
        return sb.toString();
    }

    private String buildChainLinesFromGraph(Map<String, Set<String>> callGraph, List<String> keywords) {
        if (callGraph.isEmpty()) return "";
        Set<String> calleeSet = new HashSet<>();
        for (Set<String> callees : callGraph.values()) calleeSet.addAll(callees);
        List<String> entries = keywords.stream().filter(k -> !calleeSet.contains(k)).toList();
        List<String> lines = new ArrayList<>();
        int maxDepth = 5;
        int maxLines = 12;
        for (String entry : entries) {
            if (lines.size() >= maxLines) break;
            Deque<List<String>> pathStack = new ArrayDeque<>();
            pathStack.add(new ArrayList<>(List.of(entry)));
            while (!pathStack.isEmpty() && lines.size() < maxLines) {
                List<String> path = pathStack.poll();
                if (path.size() > maxDepth) continue;
                String last = path.get(path.size() - 1);
                Set<String> nexts = callGraph.get(last);
                if (nexts == null || nexts.isEmpty()) {
                    if (path.size() >= 2) lines.add(String.join(" -> ", path));
                    continue;
                }
                for (String next : nexts) {
                    if (path.contains(next)) continue;
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(next);
                    pathStack.add(newPath);
                }
            }
        }
        if (lines.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : callGraph.entrySet()) {
                for (String t : e.getValue()) lines.add(e.getKey() + " -> " + t);
                if (lines.size() >= maxLines) break;
            }
        }
        return String.join("\n", lines);
    }

    private String runTrace(SkeletonCacheService.CachedSkeleton cached, String query, String direction, int maxDepth) {
        if (query == null || query.isBlank()) return "✗ trace 模式需要提供 query（类名或方法名）。";
        DependencyGraph graph = cached.graph();
        if (graph == null || graph.getGraph() == null) return "✗ 依赖图不可用。";
        DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> g = graph.getGraph();
        Map<String, AstNode> index = graph.getNodeIndex();

        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(index);
        List<String> candidates = nameIndex.lookup(query.strip());
        if (candidates.isEmpty()) return "✗ 未找到节点: " + query;
        String startId = pickBestCandidateId(query.strip(), candidates);
        if (startId == null) startId = candidates.get(0);
        if (!g.containsVertex(startId)) return "✗ 节点不在依赖图中: " + startId;

        Set<String> visited = new HashSet<>();
        List<String> lines = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        Map<String, Integer> depthMap = new HashMap<>();
        depthMap.put(startId, 0);
        lines.add(prettyNodeLabel(startId, index));

        while (!queue.isEmpty()) {
            String v = queue.poll();
            int d = depthMap.getOrDefault(v, 0);
            if (d >= maxDepth) continue;
            if ("both".equals(direction) || "downstream".equals(direction)) {
                for (DefaultWeightedEdge e : g.outgoingEdgesOf(v)) {
                    String t = g.getEdgeTarget(e);
                    String et = graph.getEdgeType(v, t);
                    if ("contains".equals(et)) continue;
                    if (visited.add(t)) {
                        depthMap.put(t, d + 1);
                        queue.add(t);
                        if (et == null || et.isBlank()) et = "depends";
                        lines.add("  ".repeat(d + 1) + "→ [" + et + "] " + prettyNodeLabel(t, index));
                    }
                }
            }
            if ("both".equals(direction) || "upstream".equals(direction)) {
                for (DefaultWeightedEdge e : g.incomingEdgesOf(v)) {
                    String s = g.getEdgeSource(e);
                    String et = graph.getEdgeType(s, v);
                    if ("contains".equals(et)) continue;
                    if (visited.add(s)) {
                        depthMap.put(s, d + 1);
                        queue.add(s);
                        if (et == null || et.isBlank()) et = "depends";
                        lines.add("  ".repeat(d + 1) + "← [" + et + "] " + prettyNodeLabel(s, index));
                    }
                }
            }
        }
        return "<trace_result start=\"" + startId + "\" direction=\"" + direction + "\">\n" + String.join("\n", lines) + "\n</trace_result>";
    }

    private static String prettyNodeLabel(String id, Map<String, AstNode> index) {
        if (id == null) return "";
        if (index == null) return id;
        AstNode n = index.get(id);
        if (n == null) return id;
        String fp = n.getFilePath();
        int line = n.getStartLine() + 1;
        String kind = id.startsWith("file:") ? "file" : (id.startsWith("class:") ? "class" : (id.startsWith("method:") ? "method" : "node"));
        String name = n.getName() != null ? n.getName() : id;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(kind).append("] ").append(name);
        if (fp != null && !fp.isBlank()) {
            sb.append(" -- ").append(fp.replace("\\", "/"));
            if (line > 0) sb.append(":").append(line);
        }
        return sb.toString();
    }

    private static final int SEARCH_MERGE_THRESHOLD = 10;

    private String runSearch(SkeletonCacheService.CachedSkeleton cached, String projectPath, String query) {
        if (query == null || query.isBlank()) return "✗ search 模式需要提供 query。";
        Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        if (index.isEmpty()) return "✗ 依赖图索引为空，无法 search。";

        String qRaw = query.strip();
        // 多关键词自动拆分：空格/逗号分隔，每个词独立搜索后合并
        String[] parts = qRaw.split("[\\s,]+");
        if (parts.length > 1) {
            return runMultiSearch(cached, projectPath, parts);
        }
        return runSingleSearch(cached, projectPath, qRaw);
    }

    private String runMultiSearch(SkeletonCacheService.CachedSkeleton cached, String projectPath, String[] keywords) {
        StringBuilder sb = new StringBuilder();
        sb.append("## batch search (").append(keywords.length).append(" keywords)\n");
        int totalHits = 0;
        for (String kw : keywords) {
            String t = kw != null ? kw.strip() : "";
            if (t.length() < 2) continue;
            String result = runSingleSearch(cached, projectPath, t);
            if (result != null && !result.startsWith("✗")) {
                sb.append(result).append("\n");
                totalHits++;
            }
        }
        if (totalHits == 0) {
            sb.append("✗ 所有关键词均未找到匹配\n");
            sb.append(suggestAvailableEntities(cached, keywords));
        }
        return sb.toString().trim();
    }

    private String suggestAvailableEntities(SkeletonCacheService.CachedSkeleton cached, String[] keywords) {
        List<String> samples = sampleEntityNames(cached, 8);
        if (samples.isEmpty()) return "";
        return "可用实体示例: " + String.join(", ", samples) + "\n";
    }

    private String runSingleSearch(SkeletonCacheService.CachedSkeleton cached, String projectPath, String qRaw) {
        Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
        String qLower = qRaw.toLowerCase();
        NameIndex nameIndex = cached.nameIndex() != null ? cached.nameIndex() : NameIndex.from(index);

        // Step 1: 精确匹配（nodeId / name 精确）— 命中则直接返回
        List<String> strictIds = nameIndex.lookupStrict(qRaw);
        if (!strictIds.isEmpty()) {
            return renderSearchResult(projectPath, qRaw, "strict", strictIds, index, List.of(), cached.graph());
        }

        List<String> allNodeIds = new ArrayList<>();
        List<GrepHit> allGrepHits = new ArrayList<>();
        String bestStage = "none";

        // Step 2: name 匹配
        List<String> nameIds = nameIndex.lookup(qRaw);
        if (!nameIds.isEmpty()) {
            for (String id : nameIds) {
                if (!allNodeIds.contains(id)) allNodeIds.add(id);
            }
            bestStage = "name";
        }

        // Step 3: 若总数 < 阈值，继续 AST contains 扫描
        if (allNodeIds.size() < SEARCH_MERGE_THRESHOLD) {
            List<String> astIds = new ArrayList<>();
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                String id = e.getKey();
                AstNode n = e.getValue();
                if (id == null || n == null) continue;
                String nm = n.getName();
                String sig = n.getSignature();
                boolean nameMatch = nm != null && nm.toLowerCase().contains(qLower);
                boolean sigMatch = sig != null && sig.toLowerCase().contains(qLower);
                boolean idMatch = id.toLowerCase().contains(qLower);
                if (!nameMatch && !sigMatch && idMatch && id.startsWith("method:") && id.contains(".")) {
                    String methodNameInId = id.substring(id.lastIndexOf('.') + 1);
                    if (!methodNameInId.toLowerCase().contains(qLower)) {
                        idMatch = false;
                    }
                }
                if (nameMatch || sigMatch || idMatch) {
                    if (!allNodeIds.contains(id)) astIds.add(id);
                    if (astIds.size() + allNodeIds.size() >= 60) break;
                }
            }
            for (String id : astIds) {
                if (!allNodeIds.contains(id)) allNodeIds.add(id);
            }
            if ("none".equals(bestStage) && !astIds.isEmpty()) bestStage = "ast";
        }

        // Step 4: 若总数仍 < 阈值，继续 grep
        if (allNodeIds.size() < SEARCH_MERGE_THRESHOLD) {
            Path root = (projectPath != null && !projectPath.isBlank())
                    ? Paths.get(projectPath).toAbsolutePath().normalize()
                    : Paths.get(".").toAbsolutePath().normalize();
            List<String> fileRelPaths = new ArrayList<>();
            for (Map.Entry<String, AstNode> e : index.entrySet()) {
                String id = e.getKey();
                AstNode n = e.getValue();
                if (id != null && id.startsWith("file:")) {
                    String p = n != null ? n.getFilePath() : id.substring("file:".length());
                    if (p != null && !p.isBlank()) fileRelPaths.add(p);
                }
            }
            List<GrepHit> hits = grepCodeContent(root, fileRelPaths, qRaw, 25);
            allGrepHits.addAll(hits);
            if ("none".equals(bestStage) && !hits.isEmpty()) bestStage = "grep";
        }

        if (!allNodeIds.isEmpty() || !allGrepHits.isEmpty()) {
            return renderSearchResult(projectPath, qRaw, bestStage, allNodeIds, index, allGrepHits, cached.graph());
        }

        // Step 5: 轻量模糊匹配候选（token Jaccard）
        List<String> fuzzy = fuzzyNameCandidates(nameIndex, qRaw, 12);
        if (!fuzzy.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("✗ 未找到直接匹配: ").append(qRaw).append("\n");
            sb.append("可能的相近候选（请复制其中一个再 search/expand）：\n");
            for (String s : fuzzy) sb.append("  - ").append(s).append("\n");
            return sb.toString().trim();
        }

        return buildSearchNotFoundMessage(cached, qRaw);
    }

    private String buildSearchNotFoundMessage(SkeletonCacheService.CachedSkeleton cached, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("✗ 未找到匹配: ").append(query).append("\n");
        if (query.contains(" ") || query.contains(",")) {
            String first = query.split("[\\s,]+")[0].strip();
            if (!first.isEmpty()) {
                sb.append("[提示] 请用单个关键词搜索，不要组合多个词。例如: search(\"").append(first).append("\")\n");
            }
        }
        List<String> samples = sampleEntityNames(cached, 8);
        if (!samples.isEmpty()) {
            sb.append("可用实体示例: ").append(String.join(", ", samples));
        }
        return sb.toString().trim();
    }

    private List<String> sampleEntityNames(SkeletonCacheService.CachedSkeleton cached, int limit) {
        NameIndex nameIndex = cached.nameIndex();
        if (nameIndex == null) {
            Map<String, AstNode> index = cached.graph() != null ? cached.graph().getNodeIndex() : Map.of();
            nameIndex = NameIndex.from(index);
        }
        Set<String> keys = nameIndex.lowerKeys();
        if (keys == null || keys.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            if (k == null || k.length() < 2 || k.length() > 64) continue;
            out.add(k);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static List<String> extractTopClassNames(ProjectSkeleton sk, int limit) {
        List<String> out = new ArrayList<>();
        if (sk == null || sk.getFileNodes() == null) return out;
        for (AstNode fileNode : sk.getFileNodes()) {
            if (fileNode.getChildren() == null) continue;
            for (AstNode child : fileNode.getChildren()) {
                String type = child.getNodeType();
                if (type == null) continue;
                String t = type.toLowerCase();
                if ("class".equals(t) || "interface".equals(t) || "enum".equals(t)) {
                    String name = child.getName();
                    if (name != null && !name.isBlank() && !out.contains(name)) {
                        out.add(name);
                        if (out.size() >= limit) return out;
                    }
                }
            }
        }
        return out;
    }

    private static List<String> extractTopMethodNames(ProjectSkeleton sk, int limit) {
        List<String> out = new ArrayList<>();
        if (sk == null || sk.getFileNodes() == null) return out;
        for (AstNode fileNode : sk.getFileNodes()) {
            if (fileNode.getChildren() == null) continue;
            for (AstNode child : fileNode.getChildren()) {
                if ("method".equals(child.getNodeType()) || "function".equals(child.getNodeType())) {
                    String name = child.getName();
                    if (name != null && !name.isBlank() && !out.contains(name)) {
                        out.add(name);
                        if (out.size() >= limit) return out;
                    }
                }
                if (child.getChildren() != null) {
                    for (AstNode m : child.getChildren()) {
                        if ("method".equals(m.getNodeType()) || "function".equals(m.getNodeType())) {
                            String name = m.getName();
                            if (name != null && !name.isBlank() && !out.contains(name)) {
                                out.add(name);
                                if (out.size() >= limit) return out;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static List<String> extractFileNames(ProjectSkeleton sk, int limit) {
        List<String> out = new ArrayList<>();
        if (sk == null || sk.getFileNodes() == null) return out;
        for (AstNode fileNode : sk.getFileNodes()) {
            String fp = fileNode.getFilePath();
            if (fp == null || fp.isBlank()) continue;
            String norm = fp.replace("\\", "/");
            String base = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
            if (!base.isBlank() && !out.contains(base)) {
                out.add(base);
                if (out.size() >= limit) return out;
            }
        }
        return out;
    }

    private String buildCacheHitSummary(SkeletonCacheService.CachedSkeleton cached, ProjectSkeleton sk, String projectPath) {
        int files = sk.getTotalFiles();
        int classes = sk.getTotalClasses();
        int methods = sk.getTotalMethods();
        long ageSec = (System.currentTimeMillis() - cached.buildTime()) / 1000;
        StringBuilder sb = new StringBuilder();
        sb.append("[cached] 骨架已缓存 (").append(files).append("文件/").append(classes).append("类/").append(methods).append("方法, ")
          .append(String.format("%.0f", (double) ageSec)).append("秒前构建)\n");
        sb.append("核心类:\n");
        Path root = (projectPath != null && !projectPath.isBlank())
                ? Paths.get(projectPath).toAbsolutePath().normalize() : null;
        int classCount = 0;
        final int maxClasses = 8;
        final int maxMethodsPerClass = 5;
        List<AstNode> fileNodes = sk.getFileNodes() != null ? sk.getFileNodes() : List.of();
        for (AstNode fileNode : fileNodes) {
            if (classCount >= maxClasses) break;
            if (fileNode.getChildren() == null) continue;
            String relPath = fileNode.getFilePath();
            if (relPath == null) relPath = "";
            for (AstNode child : fileNode.getChildren()) {
                if (classCount >= maxClasses) break;
                String nt = child.getNodeType();
                if (nt == null) continue;
                String ntLower = nt.toLowerCase();
                if (!"class".equals(ntLower) && !"interface".equals(ntLower) && !"enum".equals(ntLower)) continue;
                String className = DependencyGraphBuilder.extractSimpleName(child.getName(), nt);
                if (className == null || className.isBlank()) className = child.getName();
                if (className == null) className = "(unnamed)";
                int line = child.getStartLine() + 1;
                sb.append("  ").append(className).append(" (").append(relPath.replace("\\", "/")).append(":").append(line).append(") — ");
                List<String> methodNames = new ArrayList<>();
                if (child.getChildren() != null) {
                    for (AstNode m : child.getChildren()) {
                        if (methodNames.size() >= maxMethodsPerClass) break;
                        if (m != null && "method".equals(m.getNodeType())) {
                            String mn = DependencyGraphBuilder.extractSimpleName(m.getName(), "method");
                            if (mn != null && !mn.isBlank()) methodNames.add(mn);
                        }
                    }
                }
                sb.append(methodNames.isEmpty() ? "..." : String.join(", ", methodNames)).append("\n");
                classCount++;
            }
        }
        if (classCount == 0) sb.append("  (无)\n");
        sb.append("文件: ");
        List<String> fileEntries = new ArrayList<>();
        int fileShown = 0;
        for (AstNode fileNode : fileNodes) {
            if (fileShown >= 8) break;
            String relPath = fileNode.getFilePath();
            if (relPath == null || relPath.isBlank()) continue;
            String base = relPath.replace("\\", "/");
            if (base.contains("/")) base = base.substring(base.lastIndexOf('/') + 1);
            int lineCount = -1;
            if (root != null) {
                Path abs = resolveProjectFile(root, relPath);
                if (abs != null) {
                    try {
                        lineCount = Files.readAllLines(abs, StandardCharsets.UTF_8).size();
                    } catch (Exception ignored) { }
                }
            }
            fileEntries.add(lineCount >= 0 ? base + "(" + lineCount + "行)" : base);
            fileShown++;
        }
        sb.append(fileEntries.isEmpty() ? "(无)" : String.join(", ", fileEntries)).append("\n");
        DependencyGraph graph = cached.graph();
        if (graph != null && graph.getEdgeTypeIndex() != null && !graph.getEdgeTypeIndex().isEmpty()) {
            Map<String, AstNode> index = graph.getNodeIndex();
            List<String> depLines = new ArrayList<>();
            for (Map.Entry<String, String> e : graph.getEdgeTypeIndex().entrySet()) {
                if ("contains".equals(e.getValue())) continue;
                if (depLines.size() >= 5) break;
                String key = e.getKey();
                int pipe = key != null ? key.indexOf('|') : -1;
                String src = (pipe > 0 && key != null) ? key.substring(0, pipe) : key;
                String tgt = (pipe >= 0 && key != null && pipe < key.length() - 1) ? key.substring(pipe + 1) : "";
                String srcLabel = shortVertexLabel(src, index);
                String tgtLabel = shortVertexLabel(tgt, index);
                depLines.add(srcLabel + "→" + tgtLabel + "(" + e.getValue() + ")");
            }
            if (!depLines.isEmpty()) {
                sb.append("依赖: ").append(String.join(", ", depLines)).append("\n");
            }
        }
        List<SkeletonCacheService.ExploredEntry> history = skeletonCacheService.getExploreHistory(projectPath);
        if (!history.isEmpty()) {
            sb.append("--- 已探索 ---\n");
            int shown = 0;
            for (SkeletonCacheService.ExploredEntry entry : history) {
                long agoSec = (System.currentTimeMillis() - entry.timestamp()) / 1000;
                sb.append(entry.mode()).append("(\"").append(entry.query()).append("\") → ")
                  .append(entry.hitCount()).append(" hits (").append(agoSec).append("秒前)\n");
                if (++shown >= 8) break;
            }
        }
        sb.append("--- 查询示例 ---\n");
        sb.append("search(\"类名\") | expand(\"文件路径\") | expand(\"类名\") | trace(\"类名\")\n");
        sb.append("expand 支持 lineRange 参数精确定位，如 lineRange=\"100-200\"。如需重新解析请传 forceRefresh=true");
        return sb.toString();
    }

    private static String shortVertexLabel(String vertexId, Map<String, AstNode> index) {
        if (vertexId == null || vertexId.isBlank()) return vertexId;
        if (index != null) {
            AstNode n = index.get(vertexId);
            if (n != null && n.getName() != null && !n.getName().isBlank())
                return n.getName();
        }
        if (vertexId.startsWith("file:")) return vertexId.length() > 5 ? vertexId.substring(5).replace("\\", "/") : vertexId;
        if (vertexId.startsWith("class:") && vertexId.contains(":")) return vertexId.substring(vertexId.lastIndexOf(':') + 1);
        if (vertexId.startsWith("method:") && vertexId.contains(".")) return vertexId.substring(vertexId.lastIndexOf('.') + 1);
        return vertexId;
    }

    private record GrepHit(String filePath, int line, String snippet) {}

    private List<GrepHit> grepCodeContent(Path projectRoot, List<String> fileRelPaths, String query, int maxHits) {
        if (projectRoot == null || fileRelPaths == null || query == null) return List.of();
        String q = query.strip();
        if (q.length() < 3) return List.of();

        List<GrepHit> hits = new ArrayList<>();
        int scannedFiles = 0;
        for (String rel : fileRelPaths) {
            if (hits.size() >= maxHits) break;
            if (rel == null || rel.isBlank()) continue;
            scannedFiles++;
            if (scannedFiles > 200) break;

            Path abs = resolveProjectFile(projectRoot, rel);
            if (abs == null) continue;
            try {
                if (Files.size(abs) > 1024 * 1024) continue;
                List<String> lines = Files.readAllLines(abs, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null) continue;
                    if (line.toLowerCase().contains(q.toLowerCase())) {
                        String snip = line.strip();
                        if (snip.length() > 240) snip = snip.substring(0, 237) + "...";
                        hits.add(new GrepHit(rel.replace("\\", "/"), i + 1, snip));
                        if (hits.size() >= maxHits) break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return hits;
    }

    private static List<String> getTopReferencedBy(String nodeId, DependencyGraph graph,
            Map<String, AstNode> index, int max) {
        if (graph == null || graph.getGraph() == null || nodeId == null) return List.of();
        var g = graph.getGraph();
        if (!g.containsVertex(nodeId)) return List.of();
        List<String> refs = new ArrayList<>();
        for (var e : g.incomingEdgesOf(nodeId)) {
            String src = g.getEdgeSource(e);
            String et = graph.getEdgeType(src, nodeId);
            if ("contains".equals(et)) continue;
            AstNode n = index != null ? index.get(src) : null;
            String label = (n != null && n.getName() != null) ? n.getName() : src;
            String fp = (n != null && n.getFilePath() != null) ? n.getFilePath().replace("\\", "/") : "";
            int line = (n != null) ? n.getStartLine() + 1 : -1;
            String ref = label;
            if (!fp.isEmpty()) ref += " (" + fp + (line > 0 ? ":" + line : "") + ")";
            refs.add(ref);
            if (refs.size() >= max) break;
        }
        return refs;
    }

    private String renderSearchResult(String projectPath, String query, String stage, List<String> nodeIds,
                                            Map<String, AstNode> index, List<GrepHit> grepHits,
                                            DependencyGraph graph) {
        List<String> uniqueIds = nodeIds != null ? new ArrayList<>(new LinkedHashSet<>(nodeIds)) : List.of();
        int nodeCount = uniqueIds.size();
        int grepCount = grepHits != null ? grepHits.size() : 0;
        int total = nodeCount + grepCount;
        boolean preview = nodeCount > 0 && nodeCount <= 3;

        StringBuilder sb = new StringBuilder();
        String stageLabel = (nodeCount > 0 && grepCount > 0) ? (stage + "+grep") : stage;
        sb.append("## search \"").append(query.replace("\"", "\\\"")).append("\" (").append(stageLabel).append(", ").append(total).append(" hits)\n");

        int written = 0;
        int previewsAdded = 0;
        final int maxCodePreviews = 12;
        if (!uniqueIds.isEmpty()) {
            for (String id : uniqueIds) {
                if (id == null) continue;
                AstNode n = index.get(id);
                String kind = id.startsWith("file:") ? "file" : (id.startsWith("class:") ? "class" : (id.startsWith("method:") ? "method" : "node"));
                String name = n != null && n.getName() != null ? n.getName() : id;
                String fp = n != null ? n.getFilePath() : null;
                int line = n != null ? (n.getStartLine() + 1) : -1;

                sb.append("[").append(kind).append("] ").append(name);
                if (fp != null && !fp.isBlank()) {
                    sb.append(" -- ").append(fp.replace("\\", "/"));
                    if (line > 0) sb.append(":").append(line);
                }
                sb.append("\n");

                // P1: 内嵌代码预览 — 从源文件读取 3-5 行函数体，减少 expand 调用
                if (previewsAdded < maxCodePreviews && projectPath != null && !projectPath.isBlank() && n != null
                        && ("method".equals(kind) || "class".equals(kind))) {
                    String codePreview = extractCodePreview(projectPath, n, 5);
                    if (codePreview != null && !codePreview.isBlank()) {
                        sb.append("  ").append(codePreview.replace("\n", "\n  ")).append("\n");
                        previewsAdded++;
                    }
                }

                if (preview && n != null) {
                    String sig = compactOneLine(n.getSignature(), n.getNodeType() + " " + n.getName());
                    if (sig != null && !sig.isBlank()) sb.append("  ").append(sig).append("\n");
                    if ("class".equals(n.getNodeType()) || "interface".equals(n.getNodeType()) || "enum".equals(n.getNodeType())) {
                        if (n.getChildren() != null && !n.getChildren().isEmpty()) {
                            int m = 0;
                            for (AstNode ch : n.getChildren()) {
                                if (ch == null || !"method".equals(ch.getNodeType())) continue;
                                String ms = compactOneLine(ch.getSignature(), ch.getName());
                                if (ms != null && !ms.isBlank()) {
                                    sb.append("    - ").append(ms).append("\n");
                                    if (++m >= 8) break;
                                }
                            }
                            if (n.getChildren().size() > 8) sb.append("    ...\n");
                        }
                    } else if (("file".equals(n.getNodeType()) || id.startsWith("file:")) && n.getChildren() != null && !n.getChildren().isEmpty()) {
                        int c = 0;
                        for (AstNode ch : n.getChildren()) {
                            if (ch == null) continue;
                            String t = ch.getNodeType();
                            if (!"class".equals(t) && !"interface".equals(t) && !"enum".equals(t) && !"method".equals(t)) continue;
                            String cs = compactOneLine(ch.getSignature(), t + " " + ch.getName());
                            if (cs != null && !cs.isBlank()) {
                                sb.append("    - ").append(cs).append("\n");
                                if (++c >= 8) break;
                            }
                        }
                        if (n.getChildren().size() > 8) sb.append("    ...\n");
                    }
                }

                if (graph != null && graph.getGraph() != null && graph.getGraph().containsVertex(id)) {
                    List<String> refs = getTopReferencedBy(id, graph, index, 3);
                    if (!refs.isEmpty()) {
                        sb.append("  ← referenced by: ").append(String.join(", ", refs)).append("\n");
                    }
                }

                written++;
                if (written >= 30) break;
            }
        }
        if (grepHits != null && !grepHits.isEmpty()) {
            int grepWritten = 0;
            for (GrepHit h : grepHits) {
                sb.append("[grep] ").append(h.filePath).append(":").append(h.line).append(" | ").append(h.snippet).append("\n");
                if (++grepWritten >= 25) break;
            }
        }
        if (total > written + (grepHits != null ? Math.min(grepHits.size(), 25) : 0)) {
            sb.append("... 还有 ").append(Math.max(0, total - written - (grepHits != null ? Math.min(grepHits.size(), 25) : 0))).append(" 条, 用更精确的关键词缩小范围\n");
        } else if (nodeCount > written && grepCount == 0) {
            sb.append("... 还有 ").append(nodeCount - written).append(" 条, 用更精确的关键词缩小范围\n");
        }
        return sb.toString();
    }

    /**
     * 从源文件读取节点处代码预览。对 method/function 节点跳过签名参数列表，从函数体第一行开始取 maxLines 行，便于看到调用关系。
     */
    private static String extractCodePreview(String projectPath, AstNode node, int maxLines) {
        if (node == null || projectPath == null || projectPath.isBlank()) return null;
        String relPath = node.getFilePath();
        if (relPath == null || relPath.isBlank()) return null;
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        Path file = root.resolve(relPath.replace("\\", "/"));
        if (!Files.isRegularFile(file)) return null;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int start = Math.max(0, node.getStartLine());
            if (start >= lines.size()) return null;
            String nodeType = node.getNodeType();
            boolean isMethodOrFunction = "method".equals(nodeType) || "function".equals(nodeType);
            if (isMethodOrFunction) {
                int bodyStart = findMethodBodyStart(lines, start);
                if (bodyStart > start && bodyStart < lines.size()) {
                    start = bodyStart;
                }
            }
            StringBuilder sb = new StringBuilder();
            int count = 0;
            boolean inDocstring = false;
            String docQuote = null;
            for (int i = start; i < lines.size() && count < maxLines; i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Skip Python docstring (""" or ''')
                if (!inDocstring) {
                    if (trimmed.startsWith("\"\"\"") || trimmed.startsWith("'''")) {
                        docQuote = trimmed.substring(0, 3);
                        if (trimmed.length() > 3 && trimmed.endsWith(docQuote)) {
                            continue;
                        }
                        inDocstring = true;
                        continue;
                    }
                } else {
                    if (trimmed.contains(docQuote)) {
                        inDocstring = false;
                        docQuote = null;
                    }
                    continue;
                }

                if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*")
                        || trimmed.startsWith("/*") || trimmed.startsWith("*/") || trimmed.startsWith("* ")) continue;
                sb.append(trimmed);
                if (i + 1 < lines.size()) sb.append("\n");
                count++;
            }
            return sb.toString().trim();
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    /** 从 startLine 起找到函数体开始行（签名结束的下一行）。支持 ):  ){  ) ->  ); 等结尾。 */
    private static int findMethodBodyStart(List<String> lines, int startLine) {
        for (int i = startLine; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) continue;
            if (trimmed.endsWith("):") || trimmed.endsWith("){") || trimmed.endsWith(") {")
                    || trimmed.endsWith(") ->") || trimmed.endsWith(");")) {
                return i + 1;
            }
            if (trimmed.contains("):") || trimmed.contains("){") || trimmed.contains(") ->") || trimmed.contains(");")) {
                return i + 1;
            }
        }
        return startLine;
    }

    private static String compactOneLine(String signature, String fallback) {
        String s = signature != null && !signature.isBlank() ? signature : (fallback != null ? fallback : "");
        s = s.replace("\r", "");
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl);
        s = s.replaceAll("\\s+", " ").trim();
        if (s.endsWith("{")) s = s.substring(0, s.length() - 1).trim();
        if (s.length() > 200) s = s.substring(0, 197) + "...";
        return s;
    }

    private static List<String> fuzzyNameCandidates(NameIndex nameIndex, String query, int limit) {
        if (nameIndex == null || query == null) return List.of();
        String q = query.strip().toLowerCase();
        if (q.length() < 3) return List.of();

        List<String> qTokens = tokenize(q);
        if (qTokens.isEmpty()) return List.of();

        record Scored(String key, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (String key : nameIndex.lowerKeys()) {
            if (key == null || key.isBlank()) continue;
            List<String> kTokens = tokenize(key);
            if (kTokens.isEmpty()) continue;
            double j = jaccard(qTokens, kTokens);
            if (j >= 0.55) scored.add(new Scored(key, j));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<String> out = new ArrayList<>();
        for (Scored s : scored) {
            out.add(s.key);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static List<String> tokenize(String s) {
        if (s == null) return List.of();
        String norm = s.replace("_", " ").replace("-", " ").replace(":", " ");
        String[] parts = norm.split("[^a-z0-9\\.]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) continue;
            String t = p.trim();
            if (t.isEmpty()) continue;
            out.add(t);
        }
        return out;
    }

    private static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> sa = new HashSet<>(a);
        Set<String> sb = new HashSet<>(b);
        int inter = 0;
        for (String x : sa) if (sb.contains(x)) inter++;
        int union = sa.size() + sb.size() - inter;
        return union <= 0 ? 0.0 : ((double) inter) / union;
    }

    /**
     * 分析代码风格与修改意图
     * <p>
     * 启动/查询文件监听器，获取开发过程中的实时修改轨迹，
     * 供大模型推断设计意图并更新模板规范。
     *
     * @param directoryPath 要监听的目录路径
     * @param action        操作类型：start（启动监听）、stop（停止监听）、drain（获取变更事件）
     * @return 操作结果或变更事件列表
     */
    public String analyzeStyleAndIntent(
            @McpToolParam(description = "目录路径（start 时必填）") String directoryPath,
            @McpToolParam(description = "操作类型: start, stop, drain") String action) {
        try {
            return switch (action) {
                case "start" -> {
                    if (directoryPath == null || directoryPath.isBlank()) {
                        yield "✗ 启动监听需要提供目录路径";
                    }
                    watcherService.startWatching(directoryPath);
                    yield "✓ 开始监听目录: " + directoryPath;
                }
                case "stop" -> {
                    watcherService.stopWatching();
                    yield "✓ 已停止监听";
                }
                case "drain" -> {
                    var events = watcherService.drainEvents(50);
                    if (events.isEmpty()) {
                        yield "暂无新的文件变更事件（队列待处理: " + watcherService.pendingEventCount() + "）";
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("捕获到 ").append(events.size()).append(" 个变更事件:\n");
                    for (var event : events) {
                        sb.append("  - [").append(event.eventType()).append("] ")
                                .append(event.filePath()).append("\n");
                    }
                    yield sb.toString();
                }
                default -> "✗ 未知操作: " + action + "，支持: start, stop, drain";
            };
        } catch (Exception e) {
            return "✗ 意图分析操作失败: " + e.getMessage();
        }
    }

    /**
     * 物化并验证模板
     * <p>
     * 将大模型生成的代码文件写入沙盒，执行编译验证命令。
     * 验证通过后可持久化至本地模板库。
     *
     * @param workspaceName 沙盒工作区名称
     * @param filePath      沙盒内的文件路径
     * @param fileContent   文件内容
     * @param verifyCommand 验证命令（如 "mvn clean compile"）
     * @return 验证结果（成功或精简的错误摘要）
     */
    public String materializeAndVerifyTemplate(
            @McpToolParam(description = "沙盒工作区名称，如: verify-crud") String workspaceName,
            @McpToolParam(description = "沙盒内文件路径，如: src/Main.java") String filePath,
            @McpToolParam(description = "文件内容") String fileContent,
            @McpToolParam(description = "验证命令，如: mvn clean compile") String verifyCommand) {
        try {
            // 1. 写入文件到沙盒
            String fullPath = workspaceName + "/" + filePath;
            sandboxService.writeFile(fullPath, fileContent);

            // 2. 执行验证命令
            SandboxVerificationService.ExecutionResult result = sandboxService.executeCommand(verifyCommand,
                    workspaceName, 120);

            if (result.success()) {
                return String.format("""
                        ✓ 编译验证通过
                        - 工作区: %s
                        - 文件: %s
                        - 命令: %s
                        - 输出: %s

                        模板已就绪，可使用 uploadTemplate 持久化至模板库。
                        """,
                        workspaceName, filePath, verifyCommand,
                        result.stdout().length() > 500 ? result.stdout().substring(0, 500) + "..." : result.stdout());
            } else {
                // 提取精简的错误摘要（借鉴 RepoMaster 的 Information Selection）
                String errorSummary = sandboxService.extractErrorSummary(result.stderr(), 20);

                return String.format("""
                        ✗ 编译验证失败 (exit code: %d)
                        - 工作区: %s
                        - 文件: %s
                        - 命令: %s

                        错误摘要:
                        %s

                        请根据以上错误修正代码后重试。
                        """,
                        result.exitCode(), workspaceName, filePath, verifyCommand, errorSummary);
            }
        } catch (Exception e) {
            return "✗ 沙盒验证失败: " + e.getMessage();
        }
    }
}
