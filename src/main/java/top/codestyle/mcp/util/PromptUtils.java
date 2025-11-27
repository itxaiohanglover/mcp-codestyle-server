package top.codestyle.mcp.util;

import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.MetaVariable;
import top.codestyle.mcp.model.tree.TreeNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词构建工具类
 * 提供目录树构建、变量格式化等功能
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
public class PromptUtils {

    /**
     * 根据模板信息列表构建目录树
     *
     * @param list 模板元信息列表
     * @return 目录树根节点
     */
    public static TreeNode buildTree(List<MetaInfo> list) {
        TreeNode root = new TreeNode("");
        Map<String, TreeNode> cache = new HashMap<>();
        cache.put("", root);

        for (MetaInfo t : list) {
            // 构建完整路径(格式: groupId/artifactId/version/filePath)
            String fullPath = t.getGroupId() + "/" + t.getArtifactId() + "/" +
                    t.getVersion() + t.getFilePath();

            // 确保完整路径目录链已存在
            mkdir(fullPath, cache);

            // 挂载文件到目录节点
            if (t.getFilename() != null && !t.getFilename().endsWith("/")) {
                TreeNode dir = cache.get(fullPath);
                if (dir != null) {
                    String fullFilePath = fullPath + "/" + t.getFilename();
                    dir.getFiles().add(fullFilePath);
                }
            }
        }
        return root;
    }

    /**
     * 递归创建目录节点链
     *
     * @param path  完整路径
     * @param cache 节点缓存（路径->节点映射）
     */
    private static void mkdir(String path, Map<String, TreeNode> cache) {
        if (cache.containsKey(path))
            return;

        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        TreeNode parent = cache.get("");

        for (String p : parts) {
            if (p.isEmpty())
                continue;
            if (sb.length() > 0)
                sb.append("/");
            sb.append(p);
            String curPath = sb.toString();
            TreeNode node = cache.get(curPath);
            if (node == null) {
                node = new TreeNode(p);
                parent.getChildren().put(p, node);
                cache.put(curPath, node);
            }
            parent = node;
        }
    }

    /**
     * 递归构建目录树的字符串表示
     *
     * @param node   当前节点
     * @param indent 当前缩进
     * @return 格式化的目录树字符串
     */
    public static String buildTreeStr(TreeNode node, String indent) {
        StringBuilder sb = new StringBuilder();
        if (!node.getName().isEmpty()) {
            sb.append(indent).append(node.getName()).append("/\n");
        }
        node.getChildren().values().forEach(c -> sb.append(buildTreeStr(c, indent + "  ")));
        node.getFiles().forEach(f -> {
            String fileName = f.substring(f.lastIndexOf("/") + 1);
            sb.append(indent).append("└── ").append(fileName).append('\n');
        });
        return sb.toString();
    }

    /**
     * 构建变量信息的字符串表示
     *
     * @param vars 变量名->描述映射
     * @return 格式化的变量列表字符串
     */
    public static String buildVarString(Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        vars.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    /**
     * 构建模板文件字符串（包含变量和模板内容）
     *
     * @param loadTemplateFile 模板文件列表
     * @return 格式化的模板字符串
     */
    public static String buildTemplatesStr(List<LocalMetaInfo> loadTemplateFile) {
        // 构建模板内容字符串
        StringBuilder detailTemplates = new StringBuilder();
        for (LocalMetaInfo metaInfo : loadTemplateFile) {
            detailTemplates.append("```\n")
                    .append(metaInfo.getTemplateContent() != null ? metaInfo.getTemplateContent() : "")
                    .append("\n```\n");
        }
        String detailTemplatesStr = detailTemplates.toString().trim();

        // 加载并构建模板变量列表
        Map<String, String> vars = new LinkedHashMap<>();
        for (MetaInfo n : loadTemplateFile) {
            if (n.getInputVariables() == null)
                continue;
            for (MetaVariable v : n.getInputVariables()) {
                String desc = String.format("%s[%s]", v.getVariableComment(), v.getVariableType());
                vars.putIfAbsent(v.getVariableName(), desc);
            }
        }
        String variables = buildVarString(vars).trim();
        return variables + detailTemplatesStr;
    }
}
