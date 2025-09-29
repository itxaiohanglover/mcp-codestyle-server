package top.codestyle.mcp.service;


import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.codestyle.mcp.model.entity.TemplateInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static top.codestyle.mcp.service.RemoteService.createExampleTemplateInfos;

/**
 * 使用stdio传输，MCP服务器由客户端自动启动
 * 但你需要先构建服务器jar:
 *
 * <pre>
 * ./mvnw clean install -DskipTests
 * </pre>
 */
@SpringBootTest
class CodestyleServiceTest {

    public static void main(String[] args) {

        String Root_Path = "E:/kaiyuan/mcp-codestyle-server";

        var stdioParams = ServerParameters.builder("java")
                .args("-jar",
                        "-Dspring.ai.mcp.server.stdio=true",
                        "-Dspring.main.web-application-type=none",
                        "-Dlogging.pattern.console=",
                        Root_Path + "/target/mcp-codestyle-server-0.0.1.jar")
                .build();

        var transport = new StdioClientTransport(stdioParams);
        var client = McpClient.sync(transport).build();

        client.initialize();

        // 列出并展示可用的工具
        McpSchema.ListToolsResult toolsList = client.listTools();
        System.out.println("可用工具 = " + toolsList);

        // 获取模板
        McpSchema.CallToolResult codestyle = client.callTool(
                new McpSchema.CallToolRequest("get-codestyle",
                Map.of("searchText", "CRUD")));
        System.out.println("代码模板: " + codestyle);

        client.closeGracefully();
    }
    @Autowired
    private CodestyleService codestyleService;
    @Test
    void codestyleSearch() throws IOException {
        String s = CodestyleService.codestyleSearch("1");
        List<TemplateInfo> templateInfos = createExampleTemplateInfos();
//        System.out.println(s);
        List<TemplateInfo> templates = codestyleService.loadFromLocalRepo(templateInfos);
        templates.forEach(t -> {
            System.out.println("文件名：" + t.getFilename());
            System.out.println("内容：" + t.getContent());
        });
    }
}