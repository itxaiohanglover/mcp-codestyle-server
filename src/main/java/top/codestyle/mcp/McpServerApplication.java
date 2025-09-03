package top.codestyle.mcp;

import top.codestyle.mcp.service.MianshiyaService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
    @Bean
    public ToolCallbackProvider serverTools(MianshiyaService mianshiyaService) {
        return MethodToolCallbackProvider.builder().toolObjects(mianshiyaService).build();
    }

}
