package top.codestyle.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP代码模板服务器应用程序
 * 提供代码模板搜索、下载、管理等MCP工具服务
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-03
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}