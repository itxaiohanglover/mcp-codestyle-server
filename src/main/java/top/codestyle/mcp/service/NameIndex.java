package top.codestyle.mcp.service;

import top.codestyle.mcp.model.ast.AstNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 名称到节点 ID 的索引，用于 expand/search/trace 的按名查找。
 * 构建一次后可缓存到 CachedSkeleton 中复用。
 */
public final class NameIndex {
    private final Map<String, List<String>> exact;
    private final Map<String, List<String>> lower;

    NameIndex(Map<String, List<String>> exact, Map<String, List<String>> lower) {
        this.exact = exact;
        this.lower = lower;
    }

    public static NameIndex from(Map<String, AstNode> nodeIndex) {
        Map<String, List<String>> exact = new java.util.HashMap<>();
        Map<String, List<String>> lower = new java.util.HashMap<>();
        if (nodeIndex == null || nodeIndex.isEmpty()) return new NameIndex(exact, lower);

        for (Map.Entry<String, AstNode> e : nodeIndex.entrySet()) {
            String id = e.getKey();
            AstNode n = e.getValue();
            if (id == null || id.isBlank() || n == null) continue;

            index(exact, lower, id, id);

            if (id.startsWith("file:")) {
                String rel = n.getFilePath();
                if (rel == null || rel.isBlank()) rel = id.substring("file:".length());
                String norm = rel.replace("\\", "/");
                String base = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
                index(exact, lower, base, id);
                String noExt = base.contains(".") ? base.substring(0, base.lastIndexOf('.')) : base;
                if (!noExt.isBlank()) index(exact, lower, noExt, id);
            }

            String name = n.getName();
            if (name != null && !name.isBlank()) {
                index(exact, lower, name, id);
                String simpleName = DependencyGraphBuilder.extractSimpleName(name, n.getNodeType());
                if (simpleName != null && !simpleName.isBlank() && !simpleName.equals(name)) {
                    index(exact, lower, simpleName, id);
                }
                if (id.startsWith("method:") && name.contains(".")) {
                    String dotSimple = name.substring(name.lastIndexOf('.') + 1);
                    if (!dotSimple.isBlank()) index(exact, lower, dotSimple, id);
                }
            }

            String sig = n.getSignature();
            if (sig != null && !sig.isBlank()) {
                String s = sig.trim();
                if (s.length() > 120) s = s.substring(0, 120);
                index(exact, lower, s, id);
            }
        }
        return new NameIndex(exact, lower);
    }

    public List<String> lookup(String query) {
        if (query == null) return List.of();
        String q = query.strip();
        if (q.isEmpty()) return List.of();

        List<String> hit = exact.get(q);
        if (hit != null && !hit.isEmpty()) return distinctKeepOrder(hit);

        String qLower = q.toLowerCase();
        hit = lower.get(qLower);
        if (hit != null && !hit.isEmpty()) return distinctKeepOrder(hit);

        if (qLower.length() >= 3 && qLower.length() <= 64) {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : lower.entrySet()) {
                if (e.getKey().contains(qLower)) out.addAll(e.getValue());
                if (out.size() >= 20) break;
            }
            return distinctKeepOrder(out);
        }
        return List.of();
    }

    public List<String> lookupStrict(String query) {
        if (query == null) return List.of();
        String q = query.strip();
        if (q.isEmpty()) return List.of();
        List<String> hit = exact.get(q);
        if (hit != null && !hit.isEmpty()) return distinctKeepOrder(hit);
        hit = lower.get(q.toLowerCase());
        if (hit != null && !hit.isEmpty()) return distinctKeepOrder(hit);
        return List.of();
    }

    public Set<String> lowerKeys() {
        return lower.keySet();
    }

    private static void index(Map<String, List<String>> exact, Map<String, List<String>> lower, String key, String nodeId) {
        if (key == null || key.isBlank() || nodeId == null || nodeId.isBlank()) return;
        exact.computeIfAbsent(key, k -> new ArrayList<>()).add(nodeId);
        lower.computeIfAbsent(key.toLowerCase(), k -> new ArrayList<>()).add(nodeId);
    }

    private static List<String> distinctKeepOrder(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        LinkedHashSet<String> set = new LinkedHashSet<>(in);
        return new ArrayList<>(set);
    }
}
