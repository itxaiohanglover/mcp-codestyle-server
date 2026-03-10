package top.codestyle.mcp.service;

import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup;

import java.util.Arrays;

/**
 * P0: 一站式代码分析 MCP 工具。单次调用完成 skeleton + search + expand + 调用链，减少 LLM 轮次与 post-MCP 原生验证。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnCodestyleToolGroup("fast-analysis")
public class ProjectAnalysisService {

    private final CodestyleService codestyleService;

    @McpTool(
            name = "analyzeProject",
            annotations = @McpTool.McpAnnotations(title = "Code Analysis", readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
            description = "代码分析: keywords传3-6个函数名, 返回位置+签名+调用链+预览. 后续用exploreCodeContext(expand)读代码."
    )
    public String analyzeProject(
            @McpToolParam(description = "项目目录绝对路径") String projectPath,
            @McpToolParam(description = "3-6个核心函数/类名,逗号分隔,如: aquery_llm,kg_query") String keywords,
            @McpToolParam(required = false, description = "聚焦子目录,如 'src/main'") String focusPath) {
        if (keywords == null || keywords.isBlank()) {
            return "✗ 缺少参数 keywords（逗号分隔的搜索关键词）";
        }
        String[] parts = keywords.split("[,，\\s]+");
        String[] trimmed = Arrays.stream(parts).map(String::strip).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return codestyleService.runAnalyze(projectPath, trimmed, focusPath);
    }
}
