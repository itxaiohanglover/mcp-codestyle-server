package top.codestyle.mcp.model.tree;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.codestyle.mcp.model.sdk.MetaVariable;

import java.util.List;

/**
 * 节点信息
 *
 * @author 小航love666, movclantian
 * @since 2025-09-29
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Node {
    /**
     * 父节点路径
     */
    public String parent_path;
    
    /**
     * 当前节点路径
     */
    public String path;
    
    /**
     * 节点类型: 0-目录, 1-文件
     */
    public int type;
    
    /**
     * 节点名称
     */
    public String name;
    
    /**
     * 输入变量列表
     */
    public List<MetaVariable> inputVarivales;
}