package top.codestyle.mcp.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   // ← 忽略 JSON 中不认识的全部字段
/* ---------- 1. JSON 节点结构 ---------- */
public class Node {
    public String parent_path;
    public String path;
    public int type;   // 0 目录，1 文件
    public String name;
    public List<InputVariable> inputVarivales;
}