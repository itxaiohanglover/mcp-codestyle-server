package top.codestyle.mcp.service;

import org.treesitter.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.codestyle.mcp.model.ast.AstNode;
import top.codestyle.mcp.model.ast.ProjectSkeleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AST 解析微服务（深度解析版）
 * <p>
 * 利用 Tree-sitter JNI 绑定对多语言项目执行全量 AST 扫描，
 * 递归抽取类/接口/方法/枚举/字段/注解与 import 依赖，构建 ProjectSkeleton。
 * 支持资源守卫（文件大小、null-byte、目录深度）、完整签名提取、docstring、import 聚合。
 *
 * @since 2.1.0
 */
@Slf4j
@Service
public class AstParsingService {

    /** 支持的语言与对应的 Tree-sitter TSLanguage 实例 */
    private static final Map<String, TSLanguage> LANGUAGE_MAP = new LinkedHashMap<>();

    static {
        LANGUAGE_MAP.put("java", new TreeSitterJava());
        LANGUAGE_MAP.put("py", new TreeSitterPython());
        LANGUAGE_MAP.put("js", new TreeSitterJavascript());
        LANGUAGE_MAP.put("ts", new TreeSitterTypescript());
        LANGUAGE_MAP.put("tsx", new TreeSitterTypescript());
        LANGUAGE_MAP.put("go", new TreeSitterGo());
    }

    /** 容器节点类型：其子节点需递归收集（类体、接口体、块等） */
    private static final Set<String> CONTAINER_NODE_TYPES = Set.of(
            "class_body", "interface_body", "block", "statement_block",
            "body", "declaration_list", "module_body"
    );

    @Value("${codestyle.skeleton.max-file-size-bytes:1048576}")
    private long maxFileSizeBytes;

    @Value("${codestyle.skeleton.max-files-per-project:5000}")
    private int maxFilesPerProject;

    @Value("${codestyle.skeleton.max-directory-depth:15}")
    private int maxDirectoryDepth;

    @Value("${codestyle.skeleton.include-tests:false}")
    private boolean includeTests;

    /**
     * 解析指定目录下的所有受支持语言的源码文件，构建 ProjectSkeleton。
     *
     * @param projectPath 项目根目录绝对路径
     * @return 项目骨架模型
     */
    public ProjectSkeleton parseProject(String projectPath) throws IOException {
        return parseProject(projectPath, null);
    }

    /**
     * 解析指定目录（可选聚焦子路径）下的源码，构建 ProjectSkeleton。
     *
     * @param projectPath 项目根目录绝对路径
     * @param focusPath   可选，聚焦子目录相对路径（如 "src/main"），仅解析该目录下文件
     * @return 项目骨架模型
     */
    public ProjectSkeleton parseProject(String projectPath, String focusPath) throws IOException {
        Path root = Paths.get(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("路径不是目录: " + projectPath);
        }
        final Path scanRoot;
        if (focusPath != null && !focusPath.isBlank()) {
            Path resolved = root.resolve(focusPath.replace("/", FileSystems.getDefault().getSeparator())).normalize();
            if (!Files.isDirectory(resolved) || !resolved.startsWith(root)) {
                throw new IllegalArgumentException("聚焦路径无效或不在项目内: " + focusPath);
            }
            scanRoot = resolved;
        } else {
            scanRoot = root;
        }

        List<AstNode> fileNodes = new ArrayList<>();
        List<ProjectSkeleton.DependencyEdge> dependencies = new ArrayList<>();
        List<AstNode> directoryNodes = new ArrayList<>();
        Map<String, String> fileSummaries = new LinkedHashMap<>();
        int[] totalClasses = {0};
        int[] totalMethods = {0};
        int[] totalFiles = {0};
        int[] currentDepth = {0};

        Set<String> allFilePaths = new HashSet<>();
        Map<String, TSParser> parserByLang = new HashMap<>();
        for (String ext : LANGUAGE_MAP.keySet()) {
            TSParser p = new TSParser();
            p.setLanguage(LANGUAGE_MAP.get(ext));
            parserByLang.put(ext, p);
        }

        Files.walkFileTree(scanRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    currentDepth[0] = scanRoot.relativize(dir).getNameCount();
                    if (currentDepth[0] > maxDirectoryDepth) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".") || "node_modules".equals(name) ||
                            "target".equals(name) || "build".equals(name) ||
                            "__pycache__".equals(name) || "vendor".equals(name) ||
                            ".git".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (totalFiles[0] >= maxFilesPerProject) {
                        return FileVisitResult.TERMINATE;
                    }
                    String ext = getExtension(file);
                    if (ext == null || !LANGUAGE_MAP.containsKey(ext)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (shouldSkipFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        String relativePath = root.relativize(file).toString().replace('\\', '/');
                        if (!includeTests && isTestFile(relativePath)) {
                            return FileVisitResult.CONTINUE;
                        }
                        allFilePaths.add(relativePath);

                        TSParser parser = parserByLang.get(ext);
                        AstNode fileNode = parseFileAst(parser, content, relativePath, ext);
                        if (fileNode != null) {
                            fileNodes.add(fileNode);
                            totalFiles[0]++;

                            for (AstNode child : fileNode.getChildren()) {
                                String nt = child.getNodeType();
                                if ("class".equals(nt) || "interface".equals(nt) || "enum".equals(nt)) {
                                    totalClasses[0]++;
                                    totalMethods[0] += child.getChildren() != null ? child.getChildren().size() : 0;
                                } else if ("method".equals(nt)) {
                                    totalMethods[0]++;
                                }
                            }

                            List<AstNode> imports = fileNode.getChildren() != null
                                    ? fileNode.getChildren().stream()
                                    .filter(c -> "import".equals(c.getNodeType()))
                                    .collect(Collectors.toList())
                                    : Collections.emptyList();
                            if (!imports.isEmpty()) {
                                for (AstNode imp : imports) {
                                    dependencies.add(ProjectSkeleton.DependencyEdge.builder()
                                            .source(relativePath)
                                            .target(imp.getName())
                                            .type("import")
                                            .weight(1.0)
                                            .build());
                                }
                            }

                            fileSummaries.put(relativePath, buildFileSummary(fileNode));
                        }
                    } catch (Exception e) {
                        log.warn("解析文件失败 [{}]: {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

        buildDirectoryTree(scanRoot, root, directoryNodes, fileNodes);

        return ProjectSkeleton.builder()
                .projectPath(projectPath)
                .totalFiles(totalFiles[0])
                .totalClasses(totalClasses[0])
                .totalMethods(totalMethods[0])
                .fileNodes(fileNodes)
                .dependencies(dependencies)
                .directoryNodes(directoryNodes)
                .fileSummaries(fileSummaries)
                .build();
    }

    private boolean shouldSkipFile(Path file) {
        try {
            long size = Files.size(file);
            if (size > maxFileSizeBytes) return true;
            if (size == 0) return false;
            byte[] head = new byte[(int) Math.min(512, size)];
            int n = Files.newInputStream(file).read(head);
            if (n <= 0) return false;
            for (int i = 0; i < n; i++) {
                if (head[i] == 0) return true;
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    private static boolean isTestFile(String path) {
        if (path == null || path.isBlank()) return false;
        String lower = path.toLowerCase().replace("\\", "/");
        String base = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        return lower.contains("/test/") || lower.contains("/tests/")
                || base.startsWith("test_") || base.endsWith("_test.py")
                || lower.contains("/__tests__/") || lower.contains("/spec/")
                || "conftest.py".equals(base);
    }

    private void buildDirectoryTree(Path scanRoot, Path projectRoot, List<AstNode> directoryNodes, List<AstNode> fileNodes) {
        Set<String> dirPaths = new TreeSet<>();
        String rootRel = projectRoot.relativize(scanRoot).toString().replace('\\', '/');
        if (!rootRel.isEmpty()) dirPaths.add(rootRel);
        for (AstNode f : fileNodes) {
            String p = f.getFilePath();
            int last = p.lastIndexOf('/');
            if (last > 0) {
                dirPaths.add(p.substring(0, last));
            }
        }
        for (String d : dirPaths) {
            directoryNodes.add(AstNode.builder()
                    .nodeType("directory")
                    .name(d)
                    .filePath(d)
                    .startLine(0)
                    .endLine(0)
                    .children(new ArrayList<>())
                    .build());
        }
    }

    private String buildFileSummary(AstNode fileNode) {
        List<String> parts = new ArrayList<>();
        if (fileNode.getChildren() != null) {
            for (AstNode c : fileNode.getChildren()) {
                String nt = c.getNodeType();
                if ("class".equals(nt)) parts.add("class " + c.getName());
                else if ("interface".equals(nt)) parts.add("interface " + c.getName());
                else if ("enum".equals(nt)) parts.add("enum " + c.getName());
                else if ("method".equals(nt) && "file".equals(fileNode.getNodeType())) parts.add("function " + c.getName());
            }
        }
        return parts.isEmpty() ? "file" : String.join(", ", parts);
    }

    private AstNode parseFileAst(TSParser parser, String content, String relativePath, String ext) {
        TSTree tree = parser.parseString(null, content);
        if (tree == null) return null;
        try {
            TSNode rootNode = tree.getRootNode();
            String[] lines = content.split("\n", -1);
            List<AstNode> children = new ArrayList<>();
            visitNodeRecursive(rootNode, lines, relativePath, ext, children, null);

            List<AstNode> importList = children.stream().filter(c -> "import".equals(c.getNodeType())).collect(Collectors.toList());
            List<AstNode> others = children.stream().filter(c -> !"import".equals(c.getNodeType())).collect(Collectors.toList());
            if (importList.size() > 5) {
                AstNode aggregated = aggregateImports(importList, relativePath);
                others.add(0, aggregated);
            } else {
                others.addAll(0, importList);
            }

            // C3: 大文件解析诊断日志，便于排查节点未被收录原因
            if (content.length() > 50_000) {
                int[] counts = countClassesAndMethods(others);
                log.info("[AST] large file {} ({} bytes): {} classes, {} methods parsed",
                        relativePath, content.length(), counts[0], counts[1]);
            }

            return AstNode.builder()
                    .nodeType("file")
                    .name(relativePath)
                    .filePath(relativePath)
                    .startLine(0)
                    .endLine(lines.length)
                    .children(others)
                    .build();
        } catch (Exception e) {
            log.debug("Tree-sitter 解析异常 [{}]: {}", relativePath, e.getMessage());
            return null;
        }
    }

    /** C3: 递归统计类数与方法数，返回 int[2] = { classes, methods }。 */
    private static int[] countClassesAndMethods(List<AstNode> nodes) {
        int classes = 0, methods = 0;
        if (nodes == null) return new int[] { 0, 0 };
        for (AstNode n : nodes) {
            if (n == null) continue;
            if ("class".equals(n.getNodeType()) || "interface".equals(n.getNodeType()) || "enum".equals(n.getNodeType())) classes++;
            if ("method".equals(n.getNodeType())) methods++;
            int[] sub = countClassesAndMethods(n.getChildren());
            classes += sub[0];
            methods += sub[1];
        }
        return new int[] { classes, methods };
    }

    private void visitNodeRecursive(TSNode node, String[] lines, String filePath, String ext,
                                    List<AstNode> collector, AstNode parent) {
        AstNode parsed = visitNode(node, lines, filePath, ext, parent);
        if (parsed != null) {
            collector.add(parsed);
            for (int i = 0; i < node.getChildCount(); i++) {
                TSNode child = node.getChild(i);
                if (isContainerNode(child.getType())) {
                    visitNodeRecursive(child, lines, filePath, ext, parsed.getChildren(), parsed);
                }
            }
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                visitNodeRecursive(node.getChild(i), lines, filePath, ext, collector, parent);
            }
        }
    }

    private static boolean isContainerNode(String type) {
        return CONTAINER_NODE_TYPES.contains(type);
    }

    private AstNode visitNode(TSNode node, String[] lines, String filePath, String ext, AstNode parent) {
        String type = node.getType();

        switch (type) {
            case "class_declaration", "class_definition" -> {
                String name = extractChildName(node, "name", lines);
                List<String> refs = new ArrayList<>();
                List<String> ann = extractAnnotations(node, lines, filePath, ext);
                for (int i = 0; i < node.getChildCount(); i++) {
                    TSNode child = node.getChild(i);
                    if ("superclass".equals(child.getType()) || "type_list".equals(child.getType())) {
                        refs.add(extractNodeText(child, lines).trim());
                    }
                    // Python: superclasses is an argument_list
                    if ("argument_list".equals(child.getType())) {
                        collectPythonBaseClasses(child, lines, refs);
                    }
                }
                return AstNode.builder()
                        .nodeType("class")
                        .name(name != null ? name : "AnonymousClass")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractFullSignature(node, lines))
                        .docstring(extractDocstring(node, lines, ext))
                        .annotations(ann)
                        .references(refs)
                        .children(new ArrayList<>())
                        .build();
            }
            case "interface_declaration" -> {
                String name = extractChildName(node, "name", lines);
                List<String> ann = extractAnnotations(node, lines, filePath, ext);
                return AstNode.builder()
                        .nodeType("interface")
                        .name(name != null ? name : "AnonymousInterface")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractFullSignature(node, lines))
                        .docstring(extractDocstring(node, lines, ext))
                        .annotations(ann)
                        .children(new ArrayList<>())
                        .build();
            }
            case "method_declaration", "method_definition", "function_definition", "function_declaration" -> {
                String name = extractChildName(node, "name", lines);
                if (name == null) name = "anonymous";
                List<String> ann = extractAnnotations(node, lines, filePath, ext);
                String vis = extractVisibility(node, lines, ext);
                List<String> invRefs = new ArrayList<>();
                collectInvocations(node, lines, invRefs);
                return AstNode.builder()
                        .nodeType("method")
                        .name(name)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractFullSignature(node, lines))
                        .docstring(extractDocstring(node, lines, ext))
                        .annotations(ann)
                        .visibility(vis)
                        .references(invRefs)
                        .children(new ArrayList<>())
                        .build();
            }
            case "import_declaration", "import_statement" -> {
                String importText = extractNodeText(node, lines).trim();
                List<String> targets = parseImportTargets(importText, ext);
                String normalized = targets.isEmpty() ? importText : String.join(", ", targets);
                return AstNode.builder()
                        .nodeType("import")
                        .name(normalized)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .build();
            }
            case "package_declaration" -> {
                String pkgText = extractNodeText(node, lines).trim();
                return AstNode.builder()
                        .nodeType("package")
                        .name(pkgText)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .build();
            }
            case "enum_declaration", "enum_definition" -> {
                String name = extractChildName(node, "name", lines);
                List<String> ann = extractAnnotations(node, lines, filePath, ext);
                return AstNode.builder()
                        .nodeType("enum")
                        .name(name != null ? name : "AnonymousEnum")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractFullSignature(node, lines))
                        .docstring(extractDocstring(node, lines, ext))
                        .annotations(ann)
                        .children(new ArrayList<>())
                        .build();
            }
            case "field_declaration" -> {
                String name = extractChildName(node, "name", lines);
                if (name == null) {
                    for (int i = 0; i < node.getChildCount(); i++) {
                        TSNode c = node.getChild(i);
                        if ("variable_declarator".equals(c.getType()) || "declaration".equals(c.getType())) {
                            name = extractChildName(c, "name", lines);
                            break;
                        }
                    }
                }
                List<String> ann = extractAnnotations(node, lines, filePath, ext);
                return AstNode.builder()
                        .nodeType("field")
                        .name(name != null ? name : "field")
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(extractFullSignature(node, lines))
                        .annotations(ann)
                        .children(new ArrayList<>())
                        .build();
            }
            case "export_statement" -> {
                String text = extractNodeText(node, lines).trim();
                return AstNode.builder()
                        .nodeType("export")
                        .name(text)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(text)
                        .build();
            }
            case "decorator", "annotation", "marker_annotation" -> {
                String text = extractNodeText(node, lines).trim();
                return AstNode.builder()
                        .nodeType("decorator")
                        .name(text)
                        .filePath(filePath)
                        .startLine(node.getStartPoint().getRow())
                        .endLine(node.getEndPoint().getRow())
                        .signature(text)
                        .build();
            }
            case "const_declaration", "variable_declaration" -> {
                if (parent == null) {
                    String name = extractChildName(node, "name", lines);
                    return AstNode.builder()
                            .nodeType("constant")
                            .name(name != null ? name : "const")
                            .filePath(filePath)
                            .startLine(node.getStartPoint().getRow())
                            .endLine(node.getEndPoint().getRow())
                            .signature(extractFullSignature(node, lines))
                            .children(new ArrayList<>())
                            .build();
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private List<String> extractAnnotations(TSNode node, String[] lines, String filePath, String ext) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode c = node.getChild(i);
            String t = c.getType();
            if ("modifiers".equals(t)) {
                for (int j = 0; j < c.getChildCount(); j++) {
                    TSNode m = c.getChild(j);
                    if ("annotation".equals(m.getType()) || "marker_annotation".equals(m.getType())) {
                        out.add(extractNodeText(m, lines).trim());
                    }
                }
            } else if ("annotation".equals(t) || "marker_annotation".equals(t) || "decorator".equals(t)) {
                out.add(extractNodeText(c, lines).trim());
            }
        }
        return out;
    }

    private String extractVisibility(TSNode node, String[] lines, String ext) {
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode c = node.getChild(i);
            if ("modifiers".equals(c.getType())) {
                String mods = extractNodeText(c, lines);
                if (mods.contains("public")) return "public";
                if (mods.contains("private")) return "private";
                if (mods.contains("protected")) return "protected";
            }
        }
        return null;
    }

    private AstNode aggregateImports(List<AstNode> importNodes, String filePath) {
        Map<String, List<String>> byPrefix = new LinkedHashMap<>();
        for (AstNode imp : importNodes) {
            String name = imp.getName();
            if (name == null) continue;
            for (String raw : name.split(",")) {
                String one = raw != null ? raw.trim() : "";
                if (one.isEmpty()) continue;

                String prefix = "";
                String simple = one;
                if (one.contains(".")) {
                    int last = one.lastIndexOf('.');
                    prefix = one.substring(0, last);
                    simple = one.substring(last + 1).replaceAll("[;].*", "").trim();
                }
                byPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(simple);
            }
        }
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, List<String>> e : byPrefix.entrySet()) {
            List<String> names = e.getValue();
            if (names == null || names.isEmpty()) continue;
            String prefix = e.getKey() != null ? e.getKey() : "";
            if (names.size() > 4) {
                String tail = ", ...(" + names.size() + ")";
                if (prefix.isBlank()) {
                    summary.append("import {").append(String.join(", ", names.subList(0, 3))).append(tail).append("}\n");
                } else {
                    summary.append(prefix).append(".{").append(String.join(", ", names.subList(0, 3))).append(tail).append("}\n");
                }
            } else {
                if (prefix.isBlank()) {
                    if (names.size() == 1) summary.append("import ").append(names.get(0)).append("\n");
                    else summary.append("import {").append(String.join(", ", names)).append("}\n");
                } else {
                    summary.append(prefix).append(".{").append(String.join(", ", names)).append("}\n");
                }
            }
        }
        return AstNode.builder()
                .nodeType("import_summary")
                .name(summary.toString().trim())
                .filePath(filePath)
                .startLine(importNodes.get(0).getStartLine())
                .endLine(importNodes.get(importNodes.size() - 1).getEndLine())
                .children(Collections.emptyList())
                .build();
    }

    private static List<String> parseImportTargets(String importText, String ext) {
        if (importText == null || importText.isBlank()) return List.of();
        String s = importText.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return List.of();

        List<String> out = new ArrayList<>();
        String e = ext != null ? ext.toLowerCase() : "";

        if ("java".equals(e)) {
            String t = s;
            if (t.startsWith("import ")) t = t.substring("import ".length()).trim();
            if (t.startsWith("static ")) t = t.substring("static ".length()).trim();
            if (t.endsWith(";")) t = t.substring(0, t.length() - 1).trim();
            if (!t.isEmpty()) out.add(t);
            return out;
        }

        if ("py".equals(e)) {
            if (s.startsWith("import ")) {
                String rest = s.substring("import ".length()).trim();
                for (String part : rest.split(",")) {
                    String p = part.trim();
                    if (p.isEmpty()) continue;
                    int asIdx = p.indexOf(" as ");
                    if (asIdx > 0) p = p.substring(0, asIdx).trim();
                    // 避免 "import os.path" 被拆坏：只裁剪首个空格
                    int sp = p.indexOf(' ');
                    if (sp > 0) p = p.substring(0, sp).trim();
                    if (!p.isEmpty()) out.add(p);
                }
                return out;
            }
            if (s.startsWith("from ")) {
                int impIdx = s.indexOf(" import ");
                if (impIdx > 5) {
                    String mod = s.substring("from ".length(), impIdx).trim();
                    String names = s.substring(impIdx + " import ".length()).trim();
                    names = names.replace("(", "").replace(")", "");
                    if (mod.isEmpty()) return List.of();
                    if (names.equals("*")) {
                        out.add(mod);
                        return out;
                    }
                    for (String part : names.split(",")) {
                        String p = part.trim();
                        if (p.isEmpty()) continue;
                        int asIdx = p.indexOf(" as ");
                        if (asIdx > 0) p = p.substring(0, asIdx).trim();
                        int sp = p.indexOf(' ');
                        if (sp > 0) p = p.substring(0, sp).trim();
                        if (p.isEmpty()) continue;
                        out.add(mod + "." + p);
                    }
                    if (out.isEmpty()) out.add(mod);
                    return out;
                }
            }
            // 兜底：去掉可能的 "import " 前缀
            if (s.startsWith("import ")) out.add(s.substring("import ".length()).trim());
            else out.add(s);
            return out;
        }

        if ("js".equals(e) || "ts".equals(e) || "tsx".equals(e)) {
            // import ... from "module"
            int fromIdx = s.indexOf(" from ");
            if (fromIdx >= 0) {
                int q1 = s.indexOf('"', fromIdx);
                int q2 = q1 >= 0 ? s.indexOf('"', q1 + 1) : -1;
                if (q1 >= 0 && q2 > q1) {
                    out.add(s.substring(q1 + 1, q2));
                    return out;
                }
                q1 = s.indexOf('\'', fromIdx);
                q2 = q1 >= 0 ? s.indexOf('\'', q1 + 1) : -1;
                if (q1 >= 0 && q2 > q1) {
                    out.add(s.substring(q1 + 1, q2));
                    return out;
                }
            }
            // import "module"
            int q1 = s.indexOf('"');
            int q2 = q1 >= 0 ? s.indexOf('"', q1 + 1) : -1;
            if (q1 >= 0 && q2 > q1) {
                out.add(s.substring(q1 + 1, q2));
                return out;
            }
            q1 = s.indexOf('\'');
            q2 = q1 >= 0 ? s.indexOf('\'', q1 + 1) : -1;
            if (q1 >= 0 && q2 > q1) {
                out.add(s.substring(q1 + 1, q2));
                return out;
            }
            out.add(s);
            return out;
        }

        if ("go".equals(e)) {
            // import "pkg" 或 import ( "a" "b" )
            int idx = 0;
            while (idx < s.length()) {
                int q1 = s.indexOf('"', idx);
                if (q1 < 0) break;
                int q2 = s.indexOf('"', q1 + 1);
                if (q2 < 0) break;
                String pkg = s.substring(q1 + 1, q2).trim();
                if (!pkg.isEmpty()) out.add(pkg);
                idx = q2 + 1;
            }
            if (out.isEmpty() && s.startsWith("import ")) out.add(s.substring("import ".length()).trim());
            return out;
        }

        out.add(s);
        return out;
    }

    private String extractFullSignature(TSNode node, String[] lines) {
        int startRow = node.getStartPoint().getRow();
        int endRow = node.getEndPoint().getRow();
        if (startRow >= lines.length) return "";
        for (int i = startRow; i <= Math.min(endRow, lines.length - 1); i++) {
            String line = lines[i];
            if (line.contains("{") || line.trim().endsWith(":") || (line.contains("(") && line.contains(")"))) {
                return joinLines(lines, startRow, i).trim();
            }
        }
        return lines[startRow].trim();
    }

    private static String joinLines(String[] lines, int start, int endInclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= endInclusive && i < lines.length; i++) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String extractDocstring(TSNode node, String[] lines, String ext) {
        TSNode body = node.getChildByFieldName("body");
        if (body != null && body.getChildCount() > 0) {
            TSNode first = body.getChild(0);
            if ("expression_statement".equals(first.getType()) && first.getChildCount() > 0) {
                TSNode strNode = first.getChild(0);
                if ("string".equals(strNode.getType())) {
                    return extractNodeText(strNode, lines).trim();
                }
            }
        }
        int row = node.getStartPoint().getRow();
        if (row > 0) {
            String prev = lines[row - 1].trim();
            if (prev.startsWith("/**") || prev.startsWith("///") || prev.startsWith("#")) {
                StringBuilder doc = new StringBuilder();
                for (int i = row - 1; i >= 0; i--) {
                    String l = lines[i].trim();
                    doc.insert(0, l + "\n");
                    if (l.endsWith("*/") || l.startsWith("def ") || l.startsWith("class ")) break;
                }
                return doc.toString().trim();
            }
        }
        return null;
    }

    /** Python class_definition: argument_list contains base classes; extract simple names and add "extends X". */
    private void collectPythonBaseClasses(TSNode node, String[] lines, List<String> refs) {
        if (node == null || refs == null) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode c = node.getChild(i);
            if (c == null || c.isNull()) continue;
            String t = c.getType();
            if ("(".equals(t) || ")".equals(t) || ",".equals(t)) continue;
            String text = extractNodeText(c, lines).trim();
            if (text.isEmpty()) continue;
            String simple = text.contains(".") ? text.substring(text.lastIndexOf('.') + 1).trim() : text;
            simple = simple.replaceAll("[<>().\\s,].*", "").trim();
            if (!simple.isEmpty()) refs.add("extends " + simple);
        }
    }

    private void collectInvocations(TSNode node, String[] lines, List<String> result) {
        String type = node.getType();
        if ("call_expression".equals(type) || "method_invocation".equals(type) || "call".equals(type)) {
            TSNode funcNode = node.getChildByFieldName("function");
            if (funcNode == null) funcNode = node.getChildByFieldName("name");
            if (funcNode == null && node.getChildCount() > 0) funcNode = node.getChild(0);
            if (funcNode != null && !funcNode.isNull()) {
                String name = extractNodeText(funcNode, lines).trim();
                if (!name.isEmpty() && !name.contains("(")) result.add(name);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectInvocations(node.getChild(i), lines, result);
        }
    }

    private String extractChildName(TSNode node, String fieldName, String[] lines) {
        TSNode nameNode = node.getChildByFieldName(fieldName);
        if (nameNode != null && !nameNode.isNull()) {
            return extractNodeText(nameNode, lines).trim();
        }
        return null;
    }

    private String extractNodeText(TSNode node, String[] lines) {
        int startRow = node.getStartPoint().getRow();
        int endRow = node.getEndPoint().getRow();
        if (startRow >= lines.length) return "";
        if (startRow == endRow) return lines[startRow];
        StringBuilder sb = new StringBuilder();
        for (int i = startRow; i <= Math.min(endRow, lines.length - 1); i++) {
            sb.append(lines[i]);
            if (i < endRow) sb.append("\n");
        }
        return sb.toString();
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return null;
    }

    // ========================= XML 输出（保留兼容） =========================

    public String toXmlSkeleton(ProjectSkeleton skeleton, double compressionRatio) {
        StringBuilder xml = new StringBuilder();
        xml.append("<project_skeleton>\n");
        xml.append("  <summary>\n");
        xml.append("    <path>").append(escapeXml(skeleton.getProjectPath())).append("</path>\n");
        xml.append("    <total_files>").append(skeleton.getTotalFiles()).append("</total_files>\n");
        xml.append("    <total_classes>").append(skeleton.getTotalClasses()).append("</total_classes>\n");
        xml.append("    <total_methods>").append(skeleton.getTotalMethods()).append("</total_methods>\n");
        xml.append("  </summary>\n");
        xml.append("  <directory_structure>\n");
        List<AstNode> sortedFiles = skeleton.getFileNodes().stream()
                .sorted(Comparator.comparing(AstNode::getFilePath))
                .collect(Collectors.toList());
        for (AstNode fileNode : sortedFiles) {
            xml.append("    <file path=\"").append(escapeXml(fileNode.getFilePath())).append("\">\n");
            if (fileNode.getChildren() != null) {
                for (AstNode child : fileNode.getChildren()) {
                    if ("import".equals(child.getNodeType()) || "package".equals(child.getNodeType()) || "import_summary".equals(child.getNodeType())) continue;
                    appendNodeXml(xml, child, "      ");
                }
            }
            xml.append("    </file>\n");
        }
        xml.append("  </directory_structure>\n");
        if (skeleton.getDependencies() != null && !skeleton.getDependencies().isEmpty()) {
            xml.append("  <dependency_graph>\n");
            for (ProjectSkeleton.DependencyEdge edge : skeleton.getDependencies()) {
                xml.append("    <edge source=\"").append(escapeXml(edge.getSource()))
                        .append("\" target=\"").append(escapeXml(edge.getTarget()))
                        .append("\" type=\"").append(edge.getType()).append("\" />\n");
            }
            xml.append("  </dependency_graph>\n");
        }
        xml.append("</project_skeleton>");
        return xml.toString();
    }

    private void appendNodeXml(StringBuilder xml, AstNode node, String indent) {
        String tag = node.getNodeType();
        xml.append(indent).append("<").append(tag);
        if (node.getName() != null) xml.append(" name=\"").append(escapeXml(node.getName())).append("\"");
        if (node.getSignature() != null && !node.getSignature().isEmpty()) xml.append(" signature=\"").append(escapeXml(node.getSignature())).append("\"");
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            xml.append(">\n");
            for (AstNode child : node.getChildren()) {
                appendNodeXml(xml, child, indent + "  ");
            }
            xml.append(indent).append("</").append(tag).append(">\n");
        } else {
            xml.append(" />\n");
        }
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
