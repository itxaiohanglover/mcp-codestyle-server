package top.codestyle.mcp.service;

import org.springframework.beans.factory.annotation.Autowired;
import top.codestyle.mcp.model.entity.InputVariable;
import top.codestyle.mcp.model.entity.TemplateInfo;


import java.util.ArrayList;
import java.util.List;



/**
 * 模拟远程服务
 *
 * @author 文艺倾年
 * @since 2025/9/12
 */
public class RemoteService {

    public static List<TemplateInfo> codestyleSearch(String templateName) {
        // 1.匹配算法检索模板(远程做)
        // 2.检索到后进行组装TemplateInfo，然后进行返回给MCP工具
        List<TemplateInfo> templateInfos = createExampleTemplateInfos();
        return templateInfos;
    }

    public static List<TemplateInfo> createExampleTemplateInfos() {
        List<TemplateInfo> templateInfos = new ArrayList<>();
        String test1 = "controller";
        templateInfos.add(createExampleTemplateInfo(test1,1L));
        String test2 = "service";
        templateInfos.add(createExampleTemplateInfo(test2,2L));
        return templateInfos;
    }

    /**
     * 创建示例TemplateInfo对象，根据JSON数据一一映射
     *
     * @return 示例TemplateInfo对象
     */
    public static TemplateInfo createExampleTemplateInfo(String name,Long id) {
        TemplateInfo templateInfo = new TemplateInfo();

        // 设置基本字段
        templateInfo.setId(id);

        templateInfo.setGroupId("artboy");
        templateInfo.setArtifactId("CRUD");
        templateInfo.setVersion("v1.0.0");

        templateInfo.setFile_path("/src/main/java/com/air/" + name);
        templateInfo.setPath("/src/main/java/com/air/controller/" + name + ".java.ftl");
        templateInfo.setFilename(name + ".java.ftl");
        templateInfo.setDescription("备注");

        // 设置inputVarivales列表
        List<InputVariable> inputVariables = new ArrayList<>();
        if(id == 1){
            InputVariable packageNameVar = new InputVariable();
            packageNameVar.setVariableName("packageName");
            packageNameVar.setVariableType("String");
            packageNameVar.setVariableComment("项目根包名（如：com.air.order）"+name);
            inputVariables.add(packageNameVar);
        }

        if (id == 2){
            InputVariable classNamePrefixVar = new InputVariable();
            classNamePrefixVar.setVariableName("classNamePrefix");
            classNamePrefixVar.setVariableType("String");
            classNamePrefixVar.setVariableComment("实体类命名前缀（驼峰式，如：Order）"+name);
            inputVariables.add(classNamePrefixVar);
        }
        templateInfo.setInputVarivales(inputVariables);

        // 设置其他字段
        templateInfo.setSha256("d41d8cd98f00b204e9800998ecf8427e");
        return templateInfo;
    }

    public static void main(String[] args)  {
        List<TemplateInfo> templateInfos = codestyleSearch("Controller.java.ftl");
        for (TemplateInfo templateInfo : templateInfos) {
            System.out.println(templateInfo);
        }
    }
}