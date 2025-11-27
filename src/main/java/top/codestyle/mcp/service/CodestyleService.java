package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaInfo;
import top.codestyle.mcp.model.sdk.RemoteMetaConfig;
import top.codestyle.mcp.model.tree.TreeNode;
import top.codestyle.mcp.util.PromptUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码风格服务
 * 提供代码模板搜索和内容获取功能
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-09
 */
@Slf4j
@Service
public class CodestyleService {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private PromptService promptService;

    /**
     * 搜索代码模板
     * 根据模板提示词从远程仓库获取模板信息,返回目录树和模板组介绍
     *
     * @param templateKeyword 模板提示词,如: CRUD, RuoYi, controller等
     * @return 模板目录树和描述信息
     */
    @McpTool(name = "codestyleSearch", description = """
            根据模板提示词搜索代码模板库，返回匹配的模板目录树和模板组介绍。
            """)
    public String codestyleSearch(
            @McpToolParam(description = "模板提示词，如: CRUD, bankend, frontend等") String templateKeyword) {
        log.info("codestyleSearch templateKeyword={}", templateKeyword);

        try {
            // 从远程仓库获取模板配置
            RemoteMetaConfig remoteConfig = templateService.fetchRemoteMetaConfig(templateKeyword);

            if (remoteConfig == null) {
                return String.format("""
                        远程仓库不可访问,无法获取模板信息。

                        建议尝试以下模板提示词：
                        【后端】CRUD, controller, service, mapper, entity
                        【前端】CRUD, index, form, modal
                        【通用】bankend, frontend

                        请检查模板提示词是否正确，或联系管理员
                        """);
            }

            // 同步检查并更新本地仓库(必须等待下载完成才能构建目录树)
            templateService.smartDownloadTemplate(remoteConfig);

            // 从本地仓库搜索匹配的模板(限定在当前模板组范围内)
            List<MetaInfo> metaInfos = templateService.searchLocalRepository(remoteConfig.getGroupId(),
                    remoteConfig.getArtifactId());

            // 构建模板目录树结构
            TreeNode treeNode = PromptUtils.buildTree(metaInfos);
            String treeStr = PromptUtils.buildTreeStr(treeNode, "").trim();

            // 格式化并返回搜索结果
            return promptService.buildSearchResult(templateKeyword, treeStr, remoteConfig.getDescription());

        } catch (Exception e) {
            log.error("模板搜索失败: {}", e.getMessage(), e);
            return "模板搜索失败: " + e.getMessage();
        }
    }

    /**
     * 获取模板文件内容
     * 根据模板文件路径获取详细内容,包括变量说明和模板代码
     *
     * @param templatePath 模板文件路径,如:
     *                     backend/CRUD/v1.0.0/src/main/java/com/air/controller/Controller.ftl
     * @return 模板文件的详细信息(变量+内容)
     * @throws IOException 文件读取异常
     */
    @McpTool(name = "getTemplateByPath", description = "传入模板文件路径,获取模板文件的详细内容(包括变量说明和模板代码)")
    public String getTemplateByPath(
            @McpToolParam(description = "模板文件路径,如:backend/CRUD/1.0.0/src/main/java/com/air/controller/Controller.ftl") String templatePath)
            throws IOException {
        log.info("getTemplateByPath templatePath={}", templatePath);

        // 使用精确路径搜索模板
        LocalMetaInfo matchedTemplate = templateService.searchByPath(templatePath);

        // 校验搜索结果
        if (matchedTemplate == null) {
            return String.format("未找到路径为 '%s' 的模板文件,请检查路径是否正确。", templatePath);
        }

        // 构建变量信息
        Map<String, String> vars = new LinkedHashMap<>();
        if (matchedTemplate.getInputVariables() != null && !matchedTemplate.getInputVariables().isEmpty()) {
            for (var variable : matchedTemplate.getInputVariables()) {
                String desc = String.format("%s（示例：%s）[%s]",
                        variable.getVariableComment(),
                        variable.getExample(),
                        variable.getVariableType());
                vars.put(variable.getVariableName(), desc);
            }
        }

        // 使用PromptUtils格式化变量信息
        String varInfo = vars.isEmpty() ? "无变量" : PromptUtils.buildVarString(vars).trim();

        // 使用PromptService模板构建最终输出
        return promptService.buildPrompt(
                templatePath,
                varInfo,
                matchedTemplate.getTemplateContent() != null ? matchedTemplate.getTemplateContent() : "");
    }
}