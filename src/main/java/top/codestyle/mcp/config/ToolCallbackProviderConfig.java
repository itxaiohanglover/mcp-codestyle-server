package top.codestyle.mcp.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codestyle.mcp.service.CodestyleService;

@Configuration
public class ToolCallbackProviderConfig {
    /**
     * 服务工具类
     */
    @Bean
    public ToolCallbackProvider serverTools(GeneratorService generatorService) {
        return MethodToolCallbackProvider.builder().toolObjects(generatorService).build();
    }

    /**
     * 服务工具类
     */
    @Bean
    public ToolCallbackProvider codestyleTools(CodestyleService codestyleService) {
        return MethodToolCallbackProvider.builder().toolObjects(codestyleService).build();
    }
}
