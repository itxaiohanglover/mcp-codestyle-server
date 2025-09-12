package top.codestyle.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import top.codestyle.mcp.service.CodestyleService;


@SpringBootApplication
public class McpServerApplication {
    private static CodestyleService codestyleService;

    public static void main(String[] args)  {

        String json = """
                [
                  {
                    "id": 1,
                    "name": "src",
                    "size": 0,
                    "parent_path": "/",
                    "path": "/src",
                    "type": 0,
                    "comment": "源代码根目录",
                    "inputVarivales": [ // 为了增加复用性，父类的变量的子类会统一继承。
                      {
                        "variableName": "packageName",
                        "variableType": "String",
                        "variableComment": "项目根包名（如：com.air.order）"
                      },
                      {
                        "variableName": "classNamePrefix",
                        "variableType": "String",
                        "variableComment": "实体类命名前缀（驼峰式，如：Order）"
                      },
                    ],
                    "tags": ["crud", "增删改查"],
                    "sha256": "d41d8cd98f00b204e9800998ecf8427e",
                    "storage_id": 1000
                  },
                  {
                    "id": 2,
                    "name": "main",
                    "size": 0,
                    "parent_path": "/src",
                    "path": "/src/main",
                    "type": 0,
                    "comment": "",
                    "inputVarivales": [],
                    "tags": [],
                    "sha256": "d41d8cd98f00b204e9800998ecf8427e",
                    "storage_id": 1001
                  },
                  {
                    "id": 3,
                    "name": "java",
                    "size": 0,
                    "parent_path": "/src/main",
                    "path": "/src/main/java",
                    "type": 0,
                    "comment": "",
                    "inputVarivales": [],
                    "tags": [],
                    "sha256": "d41d8cd98f00b204e9800998ecf8427e",
                    "storage_id": 1002
                  },
                  {
                    "id": 4,
                    "name": "Controller.java.ftl",
                    "size": 4096,
                    "parent_path": "/src/main/java",
                    "path": "/src/main/java/Controller.java.ftl",
                    "type": 1,
                    "comment": "CRUD接口控制器模板",
                    "inputVarivales": [
                      {
                        "variableName": "businessName",
                        "variableType": "String",
                        "variableComment": "业务模块中文名（如：订单）"
                      }
                    ],
                    "tags": ["Controller"], // 这里不加入CRUD，是因为标签会默认继承父类标签
                    "sha256": "722f185c48bed892d6fa12e2b8bf1e5f8200d4a70f522fb62b6caf13cb74e",
                    "storage_id": 1003
                  }
                ]""";
        String merge = CodestyleService.merge(json);
        SpringApplication.run(McpServerApplication.class, args);
    }

}
