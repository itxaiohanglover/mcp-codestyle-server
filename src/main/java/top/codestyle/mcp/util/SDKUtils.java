package top.codestyle.mcp.util;

import top.codestyle.mcp.model.sdk.MetaVariable;
import top.codestyle.mcp.model.sdk.MetaInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程文件工具类
 */
public class SDKUtils {

    /**
     * 根据模板文件信息，拉取模板内容
     */
    public static MetaInfo downloadFile(MetaInfo req, String repositoryUrl) {
        // 远程拉取文件
        return null;
    }

    /**
     * 根据任务检索到对应的模板信息
     */
    public static List<MetaInfo> search(String searchText) {
        // 远程拉取对应的模板信息
        List<MetaInfo> metaInfos = createExampleTemplateInfos();
        return metaInfos;
    }

    public static List<MetaInfo> createExampleTemplateInfos() {
        List<MetaInfo> metaInfos = new ArrayList<>();
        String test1 = "controller";
        metaInfos.add(createExampleTemplateInfo(test1,1L));
        String test2 = "service";
        metaInfos.add(createExampleTemplateInfo(test2,2L));
        return metaInfos;
    }

    /**
     * 创建示例TemplateInfo对象，根据JSON数据一一映射
     *
     * @return 示例TemplateInfo对象
     */
    public static MetaInfo createExampleTemplateInfo(String name, Long id) {
        MetaInfo metaInfo = new MetaInfo();

        // 设置基本字段
        metaInfo.setId(id);

        metaInfo.setGroupId("artboy");
        metaInfo.setArtifactId("CRUD");
        metaInfo.setVersion("v1.0.0");

        metaInfo.setFilePath("/src/main/java/com/air/" + name);
        metaInfo.setPath("/src/main/java/com/air/controller/" + name + ".java.ftl");
        metaInfo.setFilename(name + ".java.ftl");
        metaInfo.setDescription("备注");

        // 设置inputVarivales列表
        List<MetaVariable> metaVariables = new ArrayList<>();
        if(id == 1){
            MetaVariable packageNameVar = new MetaVariable();
            packageNameVar.setVariableName("packageName");
            packageNameVar.setVariableType("String");
            packageNameVar.setVariableComment("项目根包名（如：com.air.order）"+name);
            metaVariables.add(packageNameVar);
        }

        if (id == 2){
            MetaVariable classNamePrefixVar = new MetaVariable();
            classNamePrefixVar.setVariableName("classNamePrefix");
            classNamePrefixVar.setVariableType("String");
            classNamePrefixVar.setVariableComment("实体类命名前缀（驼峰式，如：Order）"+name);
            metaVariables.add(classNamePrefixVar);
        }
        metaInfo.setInputVarivales(metaVariables);

        // 设置其他字段
        metaInfo.setSha256("d41d8cd98f00b204e9800998ecf8427e");
        return metaInfo;
    }

    public static void main(String[] args)  {
        List<MetaInfo> metaInfos = search("Controller.java.ftl");
        for (MetaInfo metaInfo : metaInfos) {
            System.out.println(metaInfo);
        }
    }
}
