package top.codestyle.mcp.service;



import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.entity.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CodestyleService {

    /**
     * 静态变量
     */
    public final static String PROMPT_TEMPLATE = """
 #目录树：
 ```
 %s
 ```
 #变量说明：
 ```
 %s
 ```
 #详细模板：
 %s
 """.strip();

    public static TreeNode buildTree(List<TemplateInfo> list) {
        TreeNode root = new TreeNode("");
        Map<String, TreeNode> cache = new HashMap<>();
        cache.put("/", root);

        for (TemplateInfo t : list) {
            // 1. 确保 file_path 目录链已存在
            mkdir(t.getFile_path(), cache);

            // 2. 挂文件（filename 不为空且不是目录标记）
            if (t.getFilename() != null && !t.getFilename().endsWith("/")) {
                TreeNode dir = cache.get(t.getFile_path());
                if (dir != null) dir.getFiles().add(t.getFilename());
            }
        }
        return root;
    }

    private static void mkdir(String path, Map<String, TreeNode> cache) {
        if (cache.containsKey(path)) return;

        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        TreeNode parent = cache.get("/");

        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append("/").append(p);
            String curPath = sb.toString();
            TreeNode node = cache.get(curPath);
            if (node == null) {
                node = new TreeNode(p);
                parent.getChildren().put(p, node);
                cache.put(curPath, node);
            }
            parent = node;
        }
    }

    /* =========================  字符串构建  ========================= */
    public static String buildTreeString(TreeNode node, String indent) {
        StringBuilder sb = new StringBuilder();
        if (!node.getName().isEmpty()) sb.append(indent).append(node.getName()).append('\n');
        node.getChildren().values().forEach(c -> sb.append(buildTreeString(c, indent + "──")));
        node.getFiles().forEach(f -> sb.append(indent).append("──").append(f).append('\n'));
        return sb.toString();
    }

    public static String buildVarString(Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        vars.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }

    /**
     * 根据任务名称搜索模板库中的代码风格模板
     *
     * @param searchText 任务名称
     * @return 模板库中的代码风格模板
     */
    @Tool(name = "get-codestyle", description = "根据任务名称搜索模板库中的代码风格模板（每次操作代码时需要先检索相关代码风格模板）")
    public static String codestyleSearch(@ToolParam(description = "searchText") String searchText) {
        // 1.根据任务名称检索模板库
        List<TemplateInfo> templateInfos = RemoteService.codestyleSearch(searchText);
//        log.info("根据任务名称检索模板库，任务名称：{}，模板库中的代码风格模板：{}", searchText, templateInfos);
        // TODO 2.处理templateInfos, 得到配置文件：目录树、变量说明、详细模板
        TreeNode treeNode;
        Map<String, String> vars;
        treeNode = buildTree(templateInfos);
        vars = new LinkedHashMap<>();
        for (TemplateInfo n : templateInfos) {
            if (n.getInputVarivales() == null) continue;
            for (InputVariable v : n.getInputVarivales()) {
                String desc = String.format("%s[%s]", v.getVariableComment(), v.getVariableType());
                vars.putIfAbsent(v.getVariableName(), desc);
            }
        }
        String rootTreeInfo = buildTreeString(treeNode, "").trim();
        String inputVariables = buildVarString(vars).trim();
        StringBuilder detailTemplates = new StringBuilder();
        // 示例代码：content一定要用```包裹```
        for (TemplateInfo ignored : templateInfos) {
            detailTemplates.append("```\n").append("我是文件内容").append("\n```\n");
        }
        // 3.组装，构建提示词
        return PROMPT_TEMPLATE.formatted(rootTreeInfo, inputVariables, detailTemplates.toString());
    }
    @Autowired
    private  ObjectMapper objectMapper;

    public List<TemplateInfo> loadFromLocalRepo(List<TemplateInfo> input) throws IOException {

        String base = System.getProperty("cache.base-path", "D:\\IPBD\\cangku");
        List<TemplateInfo> result = new ArrayList<>();

        for (TemplateInfo req : input) {          // 每个 req 只代表一个文件
            Path repo = Paths.get(base, req.getGroupId(), req.getArtifactId());
            Path metaFile = repo.resolve("meta.json");

            MetaItem meta = null;                 // 用来承载命中 meta 的那一行
            if (Files.exists(metaFile)) {
                List<MetaItem> items = objectMapper.readValue(metaFile.toFile(),
                        new TypeReference<List<MetaItem>>() {});
                // 按 filename 快速查找
                meta = items.stream()
                        .filter(it -> it.getFilename().equalsIgnoreCase(req.getFilename()))
                        .findFirst()
                        .orElse(null);
            }

            TemplateInfo out;
            if (meta != null && Files.exists(repo.resolve(meta.getFilename()))) {
                /* ===== 本地命中 ===== */
                out = new TemplateInfo();
                out.setGroupId(req.getGroupId());
                out.setArtifactId(req.getArtifactId());
                out.setFilename(meta.getFilename());
                out.setFile_path(meta.getFilePath());
                out.setPath(meta.getFilePath() + "/" + meta.getFilename());
                out.setVersion(meta.getVersion());
                out.setDescription(meta.getDescription());
                out.setSha256(meta.getSha256());

                // 变量转换
                List<InputVariable> vars = new ArrayList<>();
                for (MetaVariable mv : meta.getInputVarivales()) {
                    InputVariable v = new InputVariable();
                    v.variableName = mv.getVariableName().replace("变量名：", "").trim();
                    v.variableType = mv.getVariableType().replace("变量类型：", "").trim();
                    v.variableComment = mv.getVariableComment();
                    vars.add(v);
                }
                out.setInputVarivales(vars);

                // 读内容
                out.setContent(Files.readString(repo.resolve(meta.getFilename()), StandardCharsets.UTF_8));
            } else {
                /* ===== 本地未命中，去文件服务器拉取 ===== */
                out = downloadFromFileServer(req);
                if (out == null) continue;   // 拉取失败就跳过
            }
            result.add(out);
        }
        return result;
    }

    /**
     * 根据 groupId/artifactId/filename 到文件服务器下载模板并封装成 TemplateInfo。
     * 返回 null 表示下载失败。
     */
    private TemplateInfo downloadFromFileServer(TemplateInfo req) {
        // TODO: 实现真实下载
        return null;
    }

    /**
     * 占位方法：根据 groupId/artifactId/filename 到文件服务器下载模板。
     * 返回封装好的 TemplateInfo；若下载失败返回 null。
     */
    private TemplateInfo downloadFromFileServer(TemplateInfo t, String fileName) {
        // TODO: 实现真正的下载逻辑，例如
        // String url = "http://fileserver/template/" + t.getGroupId() + "/" + t.getArtifactId() + "/" + fileName;
        // String content = HttpUtil.get(url);
        // 然后填充 TemplateInfo 并返回
        return null;
    }
}
