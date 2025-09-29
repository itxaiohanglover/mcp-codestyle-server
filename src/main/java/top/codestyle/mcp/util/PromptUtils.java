package top.codestyle.mcp.util;

import top.codestyle.mcp.model.sdk.TemplateInfo;
import top.codestyle.mcp.model.tree.TreeNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词工具类
 * 提供模板处理相关的通用工具方法
 */
public class PromptUtils {
    /**
     * 根据模板信息列表构建目录树
     */
    public static TreeNode buildTree(List<TemplateInfo> list) {
        TreeNode root = new TreeNode("");
        Map<String, TreeNode> cache = new HashMap<>();
        cache.put("/", root);

        for (TemplateInfo t : list) {
            // 1. 确保 file_path 目录链已存在
            mkdir(t.getFile_path(), cache);

            // 2. 挂文件（filename 不为空且不是目录标记）
            if (t.getFilename() != null && !t.getFilename().endsWith("/")) {
                TreeNode dir = cache.get(t.getFile_path());
                if (dir != null) dir.getFiles().add(t.getFilename());
            }
        }
        return root;
    }

    /**
     * 创建目录节点
     */
    private static void mkdir(String path, Map<String, TreeNode> cache) {
        if (cache.containsKey(path)) return;

        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        TreeNode parent = cache.get("/");

        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append("/").append(p);
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
     * 构建目录树的字符串表示
     */
    public static String buildTreeString(TreeNode node, String indent) {
        StringBuilder sb = new StringBuilder();
        if (!node.getName().isEmpty()) sb.append(indent).append(node.getName()).append('\n');
        node.getChildren().values().forEach(c -> sb.append(buildTreeString(c, indent + "──")));
        node.getFiles().forEach(f -> sb.append(indent).append("──").append(f).append('\n'));
        return sb.toString();
    }

    /**
     * 构建变量信息的字符串表示
     */
    public static String buildVarString(Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        vars.forEach((k, v) -> sb.append("- " + k + ": " + v + '\n'));
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("test");
    }
}
