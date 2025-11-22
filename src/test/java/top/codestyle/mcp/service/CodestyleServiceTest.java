package top.codestyle.mcp.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

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
        
        String Root_Path = "C:/Users/movcl/Desktop/mcp-codestyle-server";

        var stdioParams = ServerParameters.builder("java")
                .args("-jar",
                        "-Dspring.ai.mcp.server.stdio=true",
                        "-Dspring.main.web-application-type=none",
                        "-Dlogging.pattern.console=",
                        "-Dfile.encoding=UTF-8",
                        Root_Path + "/target/mcp-codestyle-server-0.0.1.jar")
                .build();

        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transport = new StdioClientTransport(stdioParams, jsonMapper);
        var client = McpClient.sync(transport).build();

        client.initialize();

        // 列出并展示可用的工具
        McpSchema.ListToolsResult toolsList = client.listTools();
        System.err.println("可用工具 = " + toolsList);
 
        // 获取模板目录树
        McpSchema.CallToolResult codestyle = client.callTool(
                new McpSchema.CallToolRequest("codestyleSearch",
                Map.of("templateKeyword", "CRUD")));
        System.err.println("代码模板目录树: " + codestyle);

        //获取具体模板内容
        McpSchema.CallToolResult codestyleContent = client.callTool(
                new McpSchema.CallToolRequest("getTemplateByPath",
                        Map.of("templatePath", "backend/CRUD/1.0.1/src/main/java/com/air/controller/Controller.ftl")));
        System.err.println("代码模板内容: " + codestyleContent);

        client.closeGracefully();
    }
}