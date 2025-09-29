package top.codestyle.mcp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaInfo;
import top.codestyle.mcp.model.sdk.MetaVariable;
import top.codestyle.mcp.model.sdk.MetaInfo;
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
    public List<MetaInfo> search(String searchText) {
        // 远程拉取文件
        List<MetaInfo> metaInfos = SDKUtils.search(searchText);
        // 本地缓存
        return metaInfos;
    }

    /**
     * 加载模板文件
     */
    public List<LocalMetaInfo> loadTemplateFile(List<MetaInfo> metaInfos) {
        // 本地拉取
        List<LocalMetaInfo> localMetaInfos = new ArrayList<>();
        // 比对本地元信息，取出不在本地的文件配置，然后远程拉取文件
        // 填充LocalMetaInfo中的 templateContent 字段
        // 本地异步同步
        return localMetaInfos;
    }

    public List<MetaInfo> loadFromLocalRepo(List<MetaInfo> input) throws IOException {

        String base = repositoryConfig.getLocalPath();
        List<MetaInfo> result = new ArrayList<>();

        for (MetaInfo req : input) {          // 每个 req 只代表一个文件
            Path repo = Paths.get(base, req.getGroupId(), req.getArtifactId());
            Path metaFile = repo.resolve("meta.json");

            LocalMetaInfo meta = null;                 // 用来承载命中 meta 的那一行
            if (Files.exists(metaFile)) {
                List<LocalMetaInfo> items = objectMapper.readValue(metaFile.toFile(),
                        new TypeReference<List<LocalMetaInfo>>() {});
                // 按 filename 快速查找
                meta = items.stream()
                        .filter(it -> it.getFilename().equalsIgnoreCase(req.getFilename()))
                        .findFirst()
                        .orElse(null);
            }

            MetaInfo out;
            if (meta != null && Files.exists(repo.resolve(meta.getFilename()))) {
                /* ===== 本地命中 ===== */
                out = new MetaInfo();
                out.setGroupId(req.getGroupId());
                out.setArtifactId(req.getArtifactId());
                out.setFilename(meta.getFilename());
                out.setFilePath(meta.getFilePath());
                out.setPath(meta.getFilePath() + "/" + meta.getFilename());
                out.setVersion(meta.getVersion());
                out.setDescription(meta.getDescription());
                out.setSha256(meta.getSha256());

                // 变量转换
                List<MetaVariable> vars = new ArrayList<>();
                for (top.codestyle.mcp.model.meta.LocalMetaVariable mv : meta.getInputVarivales()) {
                    MetaVariable v = new MetaVariable();
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
