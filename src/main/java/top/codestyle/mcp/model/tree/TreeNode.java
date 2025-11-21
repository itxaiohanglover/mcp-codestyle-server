package top.codestyle.mcp.model.tree;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 树节点
 *
 * @author 小航love666, movclantian
 * @since 2025-09-29
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TreeNode {
    /**
     * 节点名称
     */
    String name;
    
    /**
     * 子节点映射,使用TreeMap保证字典序
     */
    Map<String, TreeNode> children = new TreeMap<>();
    
    /**
     * 当前节点下的文件列表
     */
    List<String> files = new ArrayList<>();

    public TreeNode(String name) {
        this.name = name;
    }
}