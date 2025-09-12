package top.codestyle.mcp.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   // ← 忽略 JSON 中不认识的全部字段
/* ---------- 2. 树节点 ---------- */
public class TreeNode {
    String name;
    Map<String, TreeNode> children = new TreeMap<>(); // 保证字典序
    List<String> files = new ArrayList<>();
    public TreeNode(String name) { this.name = name; }
}
