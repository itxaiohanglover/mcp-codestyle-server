package top.codestyle.mcp.service;


import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.entity.InputVariable;
import top.codestyle.mcp.model.entity.Node;
import top.codestyle.mcp.model.entity.TreeNode;

import java.util.*;

@Slf4j
@Service
public class CodestyleService {

    /**
     * 根据json返回拼装后的代码模板
     * @param jsonString json字符串
     * @return 代码模板
     */
    public static String merge(String jsonString) {
        TreeNode treeNode;
        Map<String, String> vars;
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                    .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .build();
            List<Node> nodes = mapper.readValue(jsonString, new TypeReference<>() {
            });
            treeNode = buildTree(nodes);
            vars = new LinkedHashMap<>();
            for (Node n : nodes) {
                if (n.inputVarivales == null) continue;
                for (InputVariable v : n.inputVarivales) {
                    String desc = String.format("%s[%s]", v.variableComment, v.variableType);
                    vars.putIfAbsent(v.variableName, desc);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("变量提取失败", e);
        }
        System.out.println("#目录树：\n```\n" + buildTreeString(treeNode, "").trim() + "\n```\n#变量说明：\n```\n" + buildVarString(vars).trim()+"```");
        return "#目录树：\n```\n" + buildTreeString(treeNode, "").trim() + "\n```\n#变量说明：\n```\n" + buildVarString(vars).trim();
    }

    private static TreeNode buildTree(List<Node> list) {
        TreeNode root = new TreeNode("");
        Map<String, TreeNode> dirMap = new HashMap<>();
        dirMap.put("/", root);

        list.sort(Comparator.comparingInt(n -> n.path.length()));
        for (Node n : list) {
            if (n.type == 0) {          // 目录
                if (!dirMap.containsKey(n.path)) {
                    TreeNode parent = dirMap.get(n.parent_path);
                    if (parent == null) continue;
                    TreeNode newDir = new TreeNode(n.name);
                    parent.getChildren().put(n.name, newDir);
                    dirMap.put(n.path, newDir);
                }
            } else {                    // 文件
                TreeNode parent = dirMap.get(n.parent_path);
                if (parent != null) parent.getFiles().add(n.name);
            }
        }
        return root;
    }

    /* =========================  字符串构建  ========================= */
    public static String buildTreeString(TreeNode node, String indent) {
        StringBuilder sb = new StringBuilder();
        if (!node.getName().isEmpty()) sb.append(indent).append(node.getName()).append('\n');
        node.getChildren().values().forEach(c -> sb.append(buildTreeString(c, indent + "──")));
        node.getFiles().forEach(f -> sb.append(indent).append("──").append(f).append('\n'));
        return sb.toString();
    }

    public static String buildVarString(Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        vars.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    /**
     * 根据任务名称搜索模板库中的代码风格模板
     * @param searchText 任务名称
     * @return 模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        // 匹配逻辑：searchText匹配Tags，例如CRUD。匹配到某节点后，需要返回当前节点以及子节点。
        String remoteResult = """
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
                ]
                """.strip();
        // 处理：remoteResult
        // 得到配置文件：目录树、变量说明、详细模板
        // TODO 解析得到目录树：
        String rootTreeInfo = """
                src/                          # 源代码根目录
                └── main/                    \s
                    └── java/                \s
                        └── Controller.java.ftl  # CRUD接口控制器模板
                                """.strip();
        // TODO 解析得到变量说明：
        String inputVariables = """
                - packageName: 项目根包名（如：com.air.order）[String]
                - classNamePrefix: 实体类命名前缀（驼峰式，如：Order）;[String]
                - businessName: 业务模块中文名（如：订单）[String]
                """;
        // TODO 解析得到详细模板：
        String detailTemplate = """
                package ${packageName}.${subPackageName};

                import top.continew.starter.extension.crud.enums.Api;

                import io.swagger.v3.oas.annotations.tags.Tag;

                import org.springframework.web.bind.annotation.*;

                import top.continew.starter.extension.crud.annotation.CrudRequestMapping;
                import top.air.backend.common.base.BaseController;
                import ${packageName}.model.query.${classNamePrefix}Query;
                import ${packageName}.model.req.${classNamePrefix}Req;
                import ${packageName}.model.resp.${classNamePrefix}DetailResp;
                import ${packageName}.model.resp.${classNamePrefix}Resp;
                import ${packageName}.service.${classNamePrefix}Service;

                /**
                 * ${businessName}管理 API
                 *
                 * @author ${author}
                 * @since ${datetime}
                 */
                @Tag(name = "${businessName}管理 API")
                @RestController
                @CrudRequestMapping(value = "/${apiModuleName}/${apiName}", api = {Api.PAGE, Api.DETAIL, Api.ADD, Api.UPDATE, Api.DELETE, Api.EXPORT})
                public class ${className} extends BaseController<${classNamePrefix}Service, ${classNamePrefix}Resp, ${classNamePrefix}DetailResp, ${classNamePrefix}Query, ${classNamePrefix}Req> {}
                        """.strip();
        // 组装，构建提示词
        // 2.返回代码风格模板

        StringBuilder result = new StringBuilder();
        result.append(rootTreeInfo).append("\n")
                .append(inputVariables).append("\n").
                append(detailTemplate);
        return result.toString();
    }

}
