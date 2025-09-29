package top.codestyle.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.MetaItem;
import top.codestyle.mcp.model.meta.MetaVariable;
import top.codestyle.mcp.model.sdk.InputVariable;
import top.codestyle.mcp.model.sdk.TemplateInfo;
import top.codestyle.mcp.util.SDKUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TemplateService {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryConfig repositoryConfig;

    /**
     * 加载远程配置
     */
    public List<TemplateInfo> search(String searchText) {
        // 远程拉取文件
        List<TemplateInfo> templateInfos = SDKUtils.search(searchText);
        // 本地缓存
        return templateInfos;
    }

    /**
     * 加载模板文件
     */
    public List<TemplateInfo> loadTemplateFile(String searchText) {
        // 本地拉取
        List<TemplateInfo> templateInfos = new ArrayList<>();
        templateInfos = loadTemplateFile(searchText);
        // 本地未找到，远程拉取文件
        templateInfos = SDKUtils.search(searchText);
        // 本地异步同步
        return templateInfos;
    }

    public List<TemplateInfo> loadFromLocalRepo(List<TemplateInfo> input) throws IOException {

        String base = repositoryConfig.getLocalPath();
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
                out = SDKUtils.downloadFile(req, repositoryConfig.getRemotePath());
                if (out == null) continue;   // 拉取失败就跳过
            }
            result.add(out);
        }
        return result;
    }
}
