package top.codestyle.mcp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.DependencyGraph;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骨架与依赖图缓存
 * <p>
 * 以 projectPath 为 key 缓存 ProjectSkeleton 与 DependencyGraph，
 * 支持过期时间，供 extractProjectSkeleton 写入、exploreCodeContext 读取。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class SkeletonCacheService {

    @Value("${codestyle.cache.skeleton-ttl-ms:3600000}")
    private long skeletonTtlMs;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public record CachedSkeleton(ProjectSkeleton skeleton, DependencyGraph graph, NameIndex nameIndex, long buildTime) {}

    public record ExploredEntry(String mode, String query, int hitCount, long timestamp) {}

    private record CacheEntry(CachedSkeleton data, long expireAt) {}

    private final ConcurrentHashMap<String, List<ExploredEntry>> explorationHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> analyzedKeywordsMap = new ConcurrentHashMap<>();

    private static String normalizeKey(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) return "";
        try {
            return Paths.get(projectPath).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return projectPath;
        }
    }

    public void put(String projectPath, ProjectSkeleton skeleton, DependencyGraph graph) {
        put(projectPath, skeleton, graph, null);
    }

    public void put(String projectPath, ProjectSkeleton skeleton, DependencyGraph graph, NameIndex nameIndex) {
        String key = normalizeKey(projectPath);
        if (key.isEmpty()) return;
        long now = System.currentTimeMillis();
        NameIndex idx = nameIndex != null ? nameIndex : (graph != null ? NameIndex.from(graph.getNodeIndex()) : null);
        CachedSkeleton data = new CachedSkeleton(skeleton, graph, idx, now);
        cache.put(key, new CacheEntry(data, now + skeletonTtlMs));
        log.debug("骨架缓存已写入: {} (ttl={}ms)", key, skeletonTtlMs);
    }

    public CachedSkeleton get(String projectPath) {
        String key = normalizeKey(projectPath);
        if (key.isEmpty()) return null;
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expireAt()) {
            cache.remove(key);
            return null;
        }
        return entry.data();
    }

    public void invalidate(String projectPath) {
        String key = normalizeKey(projectPath);
        cache.remove(key);
        explorationHistory.remove(key);
        analyzedKeywordsMap.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
        explorationHistory.clear();
        analyzedKeywordsMap.clear();
    }

    public void recordExplore(String projectPath, String mode, String query, int hitCount) {
        String key = normalizeKey(projectPath);
        if (key.isEmpty()) return;
        explorationHistory.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new ExploredEntry(mode != null ? mode : "", query != null ? query : "", hitCount, System.currentTimeMillis()));
    }

    public List<ExploredEntry> getExploreHistory(String projectPath) {
        String key = normalizeKey(projectPath);
        List<ExploredEntry> list = explorationHistory.get(key);
        return list != null ? List.copyOf(list) : List.of();
    }

    public void recordAnalyzedKeywords(String projectPath, Collection<String> keywords) {
        String key = normalizeKey(projectPath);
        if (key.isEmpty() || keywords == null || keywords.isEmpty()) return;
        Set<String> set = analyzedKeywordsMap.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        for (String kw : keywords) {
            if (kw == null) continue;
            String s = kw.strip();
            if (!s.isEmpty()) set.add(s);
        }
    }

    public Set<String> getAnalyzedKeywords(String projectPath) {
        String key = normalizeKey(projectPath);
        Set<String> set = analyzedKeywordsMap.get(key);
        return set != null ? Set.copyOf(set) : Set.of();
    }
}
