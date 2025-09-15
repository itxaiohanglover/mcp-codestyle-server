package top.codestyle.mcp.service;
import cn.hutool.core.io.FileUtil;
import top.codestyle.mcp.model.entity.InputVariable;
import top.codestyle.mcp.model.entity.TemplateInfo;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static List<TemplateInfo> createExampleTemplateInfos() {
        List<TemplateInfo> templateInfos = new ArrayList<>();
        templateInfos.add(createExampleTemplateInfo());
        return templateInfos;
    }
    
    /**
     * 创建示例TemplateInfo对象，根据JSON数据一一映射
     * @return 示例TemplateInfo对象
     */
    public static TemplateInfo createExampleTemplateInfo() {
        TemplateInfo templateInfo = new TemplateInfo();
        
        // 设置基本字段
        templateInfo.setId(1L);
        templateInfo.setName("src");
        templateInfo.setSize(0L);
        templateInfo.setParent_path("/");
        templateInfo.setPath("/src");
        templateInfo.setType(0);
        templateInfo.setComment("源代码根目录");
        
        // 设置inputVarivales列表
        List<InputVariable> inputVariables = new ArrayList<>();
        
        InputVariable packageNameVar = new InputVariable();
        packageNameVar.setVariableName("packageName");
        packageNameVar.setVariableType("String");
        packageNameVar.setVariableComment("项目根包名（如：com.air.order）");
        inputVariables.add(packageNameVar);
        
        InputVariable classNamePrefixVar = new InputVariable();
        classNamePrefixVar.setVariableName("classNamePrefix");
        classNamePrefixVar.setVariableType("String");
        classNamePrefixVar.setVariableComment("实体类命名前缀（驼峰式，如：Order）");
        inputVariables.add(classNamePrefixVar);
        
        templateInfo.setInputVarivales(inputVariables);
        
        // 设置tags列表
        templateInfo.setTags(Arrays.asList("crud", "增删改查"));
        
        // 设置其他字段
        templateInfo.setSha256("d41d8cd98f00b204e9800998ecf8427e");
        // 读取templates下的Java/Controller.java.ftl文件内容
        String content = FileUtil.readUtf8String("templates/java/Controller.ftl");
        templateInfo.setContent(content);
        
        return templateInfo;
    }

    public static void main(String[] args) {
        List<TemplateInfo> templateInfos = codestyleSearch("Controller.java.ftl");
        for (TemplateInfo templateInfo : templateInfos) {
            System.out.println(templateInfo);
        }
    }
}