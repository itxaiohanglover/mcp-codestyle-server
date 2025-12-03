package top.codestyle.mcp.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.config.RepositoryConfig;
import top.codestyle.mcp.model.meta.LocalMetaConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lucene本地索引服务 - 模板索引和检索
 *
 * @author movclantian
 * @since 2025-12-02
 */
@Service
@RequiredArgsConstructor
public class LuceneIndexService {

    private static final String INDEX_DIR = "lucene-index",
            F_GID = "groupId",
            F_AID = "artifactId",
            F_DESC = "description",
            F_PATH = "metaPath",
            F_CONTENT = "content";

    private final RepositoryConfig repositoryConfig;
    private final ReentrantReadWriteLock indexLock = new ReentrantReadWriteLock();
    private Directory directory;
    private Analyzer analyzer;

    /**
     * 初始化Lucene索引服务
     * 创建索引目录,初始化中文分词器,并重建索引
     *
     * @throws IOException 索引目录创建失败
     */
    @PostConstruct
    public void init() throws IOException {
        var indexPath = Paths.get(repositoryConfig.getRepositoryDir(), INDEX_DIR);
        FileUtil.mkdir(indexPath.toFile());
        directory = FSDirectory.open(indexPath);
        analyzer = new SmartChineseAnalyzer();
        rebuildIndex();
    }

    /**
     * 销毁Lucene索引服务
     * 关闭索引目录资源
     *
     * @throws IOException 关闭失败
     */
    @PreDestroy
    public void destroy() throws IOException {
        if (directory != null)
            directory.close();
    }

    /**
     * 重建索引
     * 扫描本地仓库所有meta.json文件并建立索引
     *
     * @throws IOException 索引写入失败
     */
    public void rebuildIndex() throws IOException {
        indexLock.writeLock().lock();
        try {
            var config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            try (var writer = new IndexWriter(directory, config)) {
                scanAndIndexTemplates(writer, repositoryConfig.getRepositoryDir());
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 扫描并索引模板
     *
     * @param writer   索引写入器
     * @param basePath 基础路径
     * @throws IOException 扫描失败
     */
    private void scanAndIndexTemplates(IndexWriter writer, String basePath) throws IOException {
        var baseDir = new File(basePath);
        if (!baseDir.isDirectory())
            return;
        var groupDirs = baseDir.listFiles(File::isDirectory);
        if (groupDirs == null)
            return;

        for (var groupDir : groupDirs) {
            if (INDEX_DIR.equals(groupDir.getName()))
                continue;
            var artifactDirs = groupDir.listFiles(File::isDirectory);
            if (artifactDirs == null)
                continue;
            for (var artifactDir : artifactDirs) {
                var metaFile = new File(artifactDir, "meta.json");
                if (metaFile.exists())
                    indexTemplate(writer, metaFile);
            }
        }
    }

    /**
     * 索引单个模板
     *
     * @param writer   索引写入器
     * @param metaFile meta.json文件
     */
    private void indexTemplate(IndexWriter writer, File metaFile) {
        try {
            var meta = JSONUtil.toBean(FileUtil.readUtf8String(metaFile), LocalMetaConfig.class);
            var desc = readDescription(metaFile.getParentFile(), meta);
            writer.addDocument(createDoc(meta.getGroupId(), meta.getArtifactId(), desc, metaFile.getAbsolutePath()));
        } catch (Exception ignored) {
            // 单个模板索引失败不影响其他模板
        }
    }

    /**
     * 从README.md读取描述信息
     *
     * @param artifactDir 模板目录
     * @param meta        元配置信息
     * @return 描述内容
     */
    private String readDescription(File artifactDir, LocalMetaConfig meta) {
        var configs = meta.getConfigs();
        if (configs == null || configs.isEmpty())
            return meta.getArtifactId();
        var readme = new File(artifactDir, configs.get(configs.size() - 1).getVersion() + File.separator + "README.md");
        return readme.exists() ? FileUtil.readUtf8String(readme) : meta.getArtifactId();
    }

    /**
     * 创建Lucene文档
     *
     * @param groupId    组ID
     * @param artifactId 项目ID
     * @param desc       模板描述
     * @param metaPath   meta.json路径
     * @return Lucene文档
     */
    private Document createDoc(String groupId, String artifactId, String desc, String metaPath) {
        var doc = new Document();
        doc.add(new StringField(F_GID, groupId, Field.Store.YES));
        doc.add(new StringField(F_AID, artifactId, Field.Store.YES));
        doc.add(new StringField(F_PATH, metaPath, Field.Store.YES));
        doc.add(new TextField(F_DESC, StrUtil.nullToEmpty(desc), Field.Store.YES));
        doc.add(new TextField(F_CONTENT, String.join(" ", groupId, artifactId, StrUtil.nullToEmpty(desc)),
                Field.Store.NO));
        return doc;
    }

    /**
     * 更新单个模板的索引
     *
     * @param groupId    组ID
     * @param artifactId 项目ID
     * @param desc       模板描述
     * @param metaPath   meta.json路径
     */
    public void updateIndex(String groupId, String artifactId, String desc, String metaPath) {
        indexLock.writeLock().lock();
        try {
            var config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            try (var writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(new Term(F_PATH, metaPath));
                writer.addDocument(createDoc(groupId, artifactId, desc, metaPath));
            }
        } catch (IOException ignored) {
            // 索引更新失败不影响主流程
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * 本地检索模板
     * 根据关键词检索本地索引,返回Top1匹配结果
     *
     * @param keyword 搜索关键词
     * @return 匹配的模板信息,未找到返回null
     */
    public SearchResult fetchLocalMetaConfig(String keyword) {
        indexLock.readLock().lock();
        try {
            if (!DirectoryReader.indexExists(directory)) {
                // 需要释放读锁再获取写锁重建索引
                indexLock.readLock().unlock();
                rebuildIndex();
                indexLock.readLock().lock();
            }
            try (var reader = DirectoryReader.open(directory)) {
                var parser = new QueryParser(F_CONTENT, analyzer);
                parser.setDefaultOperator(QueryParser.Operator.OR);
                var queryStr = keyword.matches(".*[+\\-&|!(){}\\[\\]^\"~*?:\\\\/].*") ? QueryParser.escape(keyword)
                        : keyword;
                var topDocs = new IndexSearcher(reader).search(parser.parse(queryStr), 1);
                if (topDocs.totalHits.value > 0) {
                    var doc = reader.storedFields().document(topDocs.scoreDocs[0].doc);
                    return new SearchResult(doc.get(F_GID), doc.get(F_AID), doc.get(F_DESC), doc.get(F_PATH));
                }
            }
        } catch (Exception ignored) {
            // 检索失败返回null
        } finally {
            indexLock.readLock().unlock();
        }
        return null;
    }

    /**
     * 检索结果记录
     *
     * @param groupId     组ID
     * @param artifactId  项目ID
     * @param description 模板描述
     * @param metaPath    meta.json路径
     */
    public record SearchResult(String groupId, String artifactId, String description, String metaPath) {
    }
}
