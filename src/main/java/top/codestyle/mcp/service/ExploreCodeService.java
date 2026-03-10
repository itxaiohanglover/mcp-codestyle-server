package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup;

/**
 * 仅当 codestyle.tool-group 包含 explore 时注册，暴露 exploreCodeContext 工具。
 * analyze 组 = fast-analysis（analyzeProject） + explore（exploreCodeContext），共 2 个工具；默认组 all 时也会注册。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnCodestyleToolGroup("explore")
public class ExploreCodeService {

    private final CodestyleService codestyleService;

    @McpTool(name = "exploreCodeContext", description = "[必填projectPath,mode,query] 代码读取: "
            + "expand(批量读代码,支持file:start-end|f2), search(按名搜索), trace(上下游调用链).",
            annotations = @McpTool.McpAnnotations(title = "Explore Code Context", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String exploreCodeContext(
            @McpToolParam(description = "项目目录绝对路径（analyzeProject 会自动缓存骨架）") String projectPath,
            @McpToolParam(description = "模式: expand / trace / search") String mode,
            @McpToolParam(description = "查询目标: 文件路径、类名、方法名或搜索关键词。expand 支持 | 分隔批量，如 'file1.py:100-200|file2.py:300-400'") String query,
            @McpToolParam(required = false, description = "trace 模式方向: upstream / downstream / both (默认 both)") String direction,
            @McpToolParam(required = false, description = "最大遍历深度 (默认 3)") Integer maxDepth,
            @McpToolParam(required = false, description = "expand 模式可选: 行范围如 '100-200'（1基，含端点）") String lineRange) {
        return codestyleService.exploreCodeContext(projectPath, mode, query, direction, maxDepth, lineRange);
    }
}
