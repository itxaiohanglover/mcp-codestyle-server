package top.codestyle.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codestyle.mcp.service.CodestyleService;

/**
 * MCP工具回调提供者配置
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-03
 */
@Configuration
public class ToolCallbackProviderConfig {

    /**
     * 注册代码模板服务工具
     *
     * @param codestyleService 代码模板服务
     * @return 工具回调提供者
     */
    @Bean
    public ToolCallbackProvider codestyleTools(CodestyleService codestyleService) {
        return MethodToolCallbackProvider.builder().toolObjects(codestyleService).build();
    }
}