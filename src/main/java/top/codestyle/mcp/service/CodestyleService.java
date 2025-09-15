package top.codestyle.mcp.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.entity.InputVariable;
import top.codestyle.mcp.model.entity.TemplateInfo;
import top.codestyle.mcp.model.entity.TreeNode;

import java.util.*;

@Slf4j
@Service
public class CodestyleService {

    /**
     * 静态变量
     */
    public final static String PROMPT_TEMPLATE = """
            #目录树：
            ```
            %s
            ```
            #变量说明：
            ```
            %s
            ```
            #详细模板：
            %s
            """.strip();

//    public static String merge(List<TemplateInfo> templateInfos) {
//        TreeNode treeNode;
//        Map<String, String> vars;
//        try {
//
//            treeNode = buildTree(templateInfos);
//            vars = new LinkedHashMap<>();
//            for (TemplateInfo n : templateInfos) {
//                if (n.getInputVarivales() == null) continue;
//                for (InputVariable v : n.getInputVarivales()) {
//                    String desc = String.format("%s[%s]", v.getVariableComment(), v.getVariableType());
//                    vars.putIfAbsent(v.getVariableName(), desc);
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException("变量提取失败", e);
//        }
//        return "#目录树：\n```\n" + buildTreeString(treeNode, "").trim() + "\n```\n#变量说明：\n```\n" + buildVarString(vars).trim();
//    }

    private static TreeNode buildTree(List<TemplateInfo> list) {
        TreeNode root = new TreeNode("");
        Map<String, TreeNode> dirMap = new HashMap<>();
        dirMap.put("/", root);

        list.sort(Comparator.comparingInt(n -> n.getPath().length()));
        for (TemplateInfo n : list) {
            if (n.getType() == 0) {          // 目录
                if (!dirMap.containsKey(n.getPath())) {
                    TreeNode parent = dirMap.get(n.getParent_path());
                    if (parent == null) continue;
                    TreeNode newDir = new TreeNode(n.getName());
                    parent.getChildren().put(n.getName(), newDir);
                    dirMap.put(n.getPath(), newDir);
                }
            } else {                    // 文件
                TreeNode parent = dirMap.get(n.getParent_path());
                if (parent != null) parent.getFiles().add(n.getName());
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
     *
     * @param searchText 任务名称
     * @return 模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        List<TemplateInfo> templateInfos = RemoteService.codestyleSearch(searchText);
        log.info("根据任务名称检索模板库，任务名称：{}，模板库中的代码风格模板：{}", searchText, templateInfos);
        // TODO 2.处理templateInfos, 得到配置文件：目录树、变量说明、详细模板
        TreeNode treeNode;
        Map<String, String> vars;
        treeNode = buildTree(templateInfos);
        vars = new LinkedHashMap<>();
        for (TemplateInfo n : templateInfos) {
            if (n.getInputVarivales() == null) continue;
            for (InputVariable v : n.getInputVarivales()) {
                String desc = String.format("%s[%s]", v.getVariableComment(), v.getVariableType());
                vars.putIfAbsent(v.getVariableName(), desc);
            }
        }
        String rootTreeInfo = buildTreeString(treeNode, "").trim();
        String inputVariables = buildVarString(vars).trim();
        StringBuilder detailTemplates = new StringBuilder();
        // 示例代码：content一定要用```包裹```
        for (TemplateInfo templateInfo : templateInfos) {
            detailTemplates.append("```\n").append(templateInfo.getContent()).append("\n```\n");
        }
        // 3.组装，构建提示词
        return PROMPT_TEMPLATE.formatted(rootTreeInfo, inputVariables, detailTemplates.toString());
    }

}
