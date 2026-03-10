package top.codestyle.mcp.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 根据 codestyle.tool-group 决定是否注册某组 MCP 工具。
 *
 * 工具组说明：
 *   all（默认）→ 全部工具；analyze → fast-analysis（analyzeProject）+ explore（exploreCodeContext），共 2 个工具
 *   fast-analysis   → 仅 ProjectAnalysisService（1 工具 analyzeProject）
 *   explore         → 仅 ExploreCodeService（1 工具 exploreCodeContext）
 *   code-analysis   → 仅 CodeAnalysisService（1 工具 extractProjectSkeleton）
 *   template        → 仅 TemplateToolService（7 工具）
 *   all             → 全部注册
 */
public class CodestyleToolGroupCondition implements Condition {

    private static final String PROP = "codestyle.tool-group";
    private static final String DEFAULT_GROUP = "all";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        String group = env.getProperty(PROP, DEFAULT_GROUP);
        if (group == null || group.isBlank()) group = DEFAULT_GROUP;

        Map<String, Object> attrs = metadata.getAnnotationAttributes(
                "top.codestyle.mcp.config.ConditionalOnCodestyleToolGroup");
        if (attrs == null) return false;
        String value = (String) attrs.get("value");
        if (value == null || value.isBlank()) return false;

        if ("all".equalsIgnoreCase(group)) return true;
        if ("analyze".equalsIgnoreCase(group)) {
            // 默认组：analyzeProject + exploreCodeContext，不含 extractProjectSkeleton
            return "fast-analysis".equalsIgnoreCase(value)
                    || "explore".equalsIgnoreCase(value);
        }
        return value.equalsIgnoreCase(group);
    }
}
