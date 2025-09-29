package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.sdk.InputVariable;
import top.codestyle.mcp.model.sdk.TemplateInfo;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.PromptUtils;
import java.util.*;

@Slf4j
@Service
public class CodestyleService {

    // 提示词管理
    @Autowired
    private PromptTemplateService promptTemplateService;

    // 模板服务
    @Autowired
    private TemplateService templateService;

    /**
     * 根据任务名称搜索模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle-template", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        List<TemplateInfo> templateInfos = templateService.search(searchText);
        // 2.处理templateInfos, 得到配置文件：目录树、变量说明、详细模板
        TreeNode treeNode = PromptUtils.buildTree(templateInfos);
        Map<String, String> vars = new LinkedHashMap<>();
        
        for (TemplateInfo n : templateInfos) {
            if (n.getInputVarivales() == null) continue;
            for (InputVariable v : n.getInputVarivales()) {
                String desc = String.format("%s[%s]", v.getVariableComment(), v.getVariableType());
                vars.putIfAbsent(v.getVariableName(), desc);
            }
        }
        
        // 使用TemplateUtils工具类
        String rootTreeInfo = PromptUtils.buildTreeString(treeNode, "").trim();
        String inputVariables = PromptUtils.buildVarString(vars).trim();
        StringBuilder detailTemplates = new StringBuilder();
        
        // 构建详细模板内容
        for (TemplateInfo templateInfo : templateInfos) {
            detailTemplates.append("```\n").append(templateInfo.getContent() != null ? templateInfo.getContent() : "").append("\n```\n");
        }
        
        // 3.组装，构建提示词
        return promptTemplateService.buildPrompt(rootTreeInfo, inputVariables, detailTemplates.toString());
    }
}