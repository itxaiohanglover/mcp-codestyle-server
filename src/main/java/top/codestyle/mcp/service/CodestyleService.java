package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.PromptUtils;
import java.util.*;

@Slf4j
@Service
public class CodestyleService {

    // 提示词管理
    @Autowired
    private PromptService promptTemplateService;

    // 模板服务
    @Autowired
    private TemplateService templateService;

    /**
     * 根据任务名称搜索模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle-template", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        List<MetaInfo> metaInfos = templateService.search(searchText);
        // 2.处理templateInfos, 得到提示词：目录树、模板信息：模板变量+模板内容
        // 2.1加载目录树
        TreeNode treeNode = PromptUtils.buildTree(metaInfos);
        String rootTreeInfo = PromptUtils.buildTreeStr(treeNode, "").trim();
        // TODO 2.2加载模板内容:填充对应的 templateContent 字段
        List<LocalMetaInfo> loadTemplateFile = templateService.loadTemplateFile(metaInfos);
        String templatesStr = PromptUtils.buildTemplatesStr(loadTemplateFile);
        // 3.组装，构建提示词
        return promptTemplateService.buildPrompt(rootTreeInfo, templatesStr);
    }
}