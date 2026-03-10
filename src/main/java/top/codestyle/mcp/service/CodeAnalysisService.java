package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup;

/**
 * 仅当 codestyle.tool-group=code-analysis 或 all 时注册，暴露 extractProjectSkeleton 工具。
 * 普通使用场景也可用 analyze 组：analyzeProject + exploreCodeContext（已自动构建骨架）；默认 all 时全部工具均注册。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnCodestyleToolGroup("code-analysis")
public class CodeAnalysisService {

    private final CodestyleService codestyleService;

    @McpTool(name = "extractProjectSkeleton", description = "[必填projectPath] AST解析项目骨架. 首次调用返回骨架,后续自动缓存. 再调exploreCodeContext探索代码.",
            annotations = @McpTool.McpAnnotations(title = "Extract Project Skeleton", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public String extractProjectSkeleton(
            @McpToolParam(description = "项目目录绝对路径") String projectPath,
            @McpToolParam(required = false, description = "详情级别 1-4: 1=目录概览, 2=类骨架(推荐), 3=方法签名, 4=完整") Integer detailLevel,
            @McpToolParam(required = false, description = "可选: 聚焦的子目录路径(如 src/main)，仅解析该目录") String focusPath,
            @McpToolParam(required = false, description = "强制刷新缓存(默认false)") Boolean forceRefresh) {
        return codestyleService.extractProjectSkeleton(projectPath, detailLevel, focusPath, forceRefresh);
    }
}
