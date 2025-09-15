package top.codestyle.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import top.codestyle.mcp.service.CodestyleService;


@SpringBootApplication
public class McpServerApplication {


    public static void main(String[] args)  {
        CodestyleService codestyleService = new CodestyleService();
        String s = codestyleService.codestyleSearch("1");
        System.out.println(s);
        SpringApplication.run(McpServerApplication.class, args);
    }

}
