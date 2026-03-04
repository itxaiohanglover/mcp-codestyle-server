package top.codestyle.mcp.util;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.Data;
import top.codestyle.mcp.model.template.TemplateVariable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AST-based Java template generator.
 * Converts stable code anchors into FreeMarker placeholders using AST ranges.
 */
public final class AstTemplateGenerator {

    private static final String PACKAGE_NAME_VAR = "packageName";
    private static final String CLASS_NAME_VAR = "className";
    private static final String MODULE_NAME_VAR = "moduleName";

    private static final String PACKAGE_NAME_PLACEHOLDER = "${packageName}";
    private static final String CLASS_NAME_PLACEHOLDER = "${className}";
    private static final String MODULE_NAME_PLACEHOLDER = "${moduleName}";

    private static final Set<String> ROUTE_ANNOTATIONS = Set.of(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");

    private static final List<String> CLASS_SUFFIXES = List.of(
            "ServiceImpl", "Controller", "Service", "Mapper");

    private AstTemplateGenerator() {
    }

    /**
     * Generate .ftl content and input variables from raw Java code.
     *
     * @param rawJavaCode raw Java source code
     * @return generated result
     * @throws ParseProblemException if JavaParser cannot parse the code
     */
    public static GenerateResult generate(String rawJavaCode) throws ParseProblemException {
        if (rawJavaCode == null || rawJavaCode.isBlank()) {
            throw new IllegalArgumentException("rawJavaCode must not be blank");
        }

        String normalizedSource = normalizeLineEndings(rawJavaCode);
        CompilationUnit compilationUnit = StaticJavaParser.parse(normalizedSource);
        ExtractionContext context = new ExtractionContext(normalizedSource, buildLineStartOffsets(normalizedSource));

        new AnchorVisitor().visit(compilationUnit, context);

        GenerateResult result = new GenerateResult();
        result.setFtlContent(applyReplacements(normalizedSource, context.getReplacements()));
        result.setVariables(buildVariables(context));
        return result;
    }

    private static List<TemplateVariable> buildVariables(ExtractionContext context) {
        List<TemplateVariable> variables = new ArrayList<>();

        if (context.getPackageName() != null && !context.getPackageName().isBlank()) {
            variables.add(buildVariable(PACKAGE_NAME_VAR, "包名", context.getPackageName()));
        }
        if (context.getClassName() != null && !context.getClassName().isBlank()) {
            variables.add(buildVariable(CLASS_NAME_VAR, "基础类名（已剥离 Controller/ServiceImpl/Mapper 后缀）", context.getClassName()));
        }
        if (context.getModuleName() != null && !context.getModuleName().isBlank()) {
            variables.add(buildVariable(MODULE_NAME_VAR, "模块路由名（来自 Mapping 注解路径）", context.getModuleName()));
        }

        return variables;
    }

    private static TemplateVariable buildVariable(String variableName, String variableComment, String example) {
        TemplateVariable variable = new TemplateVariable();
        variable.setVariableName(variableName);
        variable.setVariableType("String");
        variable.setVariableComment(variableComment);
        variable.setExample(example);
        return variable;
    }

    private static String applyReplacements(String source, List<Replacement> replacements) {
        if (replacements.isEmpty()) {
            return source;
        }

        List<Replacement> ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(Replacement::getStart).reversed());

        StringBuilder builder = new StringBuilder(source);
        int overlapBoundary = source.length();
        for (Replacement replacement : ordered) {
            if (replacement == null || replacement.getStart() < 0 || replacement.getEnd() > source.length()
                    || replacement.getStart() >= replacement.getEnd()) {
                continue;
            }
            if (replacement.getEnd() > overlapBoundary) {
                continue;
            }
            builder.replace(replacement.getStart(), replacement.getEnd(), replacement.getValue());
            overlapBoundary = replacement.getStart();
        }
        return builder.toString();
    }

    private static String normalizeLineEndings(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static int[] buildLineStartOffsets(String source) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        int[] offsets = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) {
            offsets[i] = starts.get(i);
        }
        return offsets;
    }

    private static int toOffset(int[] lineStartOffsets, Position position) {
        int lineIndex = position.line - 1;
        if (lineIndex < 0 || lineIndex >= lineStartOffsets.length) {
            throw new IllegalArgumentException("Invalid position line: " + position.line);
        }
        return lineStartOffsets[lineIndex] + position.column - 1;
    }

    private static String stripClassSuffix(String className) {
        if (className == null || className.isBlank()) {
            return className;
        }
        for (String suffix : CLASS_SUFFIXES) {
            if (className.endsWith(suffix) && className.length() > suffix.length()) {
                return className.substring(0, className.length() - suffix.length());
            }
        }
        return className;
    }

    private static boolean isRouteAnnotation(AnnotationExpr annotationExpr) {
        return ROUTE_ANNOTATIONS.contains(annotationExpr.getName().getIdentifier());
    }

    private static Optional<StringLiteralExpr> extractPathLiteral(AnnotationExpr annotationExpr) {
        if (annotationExpr instanceof SingleMemberAnnotationExpr singleMember) {
            return extractStringLiteral(singleMember.getMemberValue());
        }
        if (annotationExpr instanceof NormalAnnotationExpr normalAnnotation) {
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String key = pair.getNameAsString();
                if ("value".equals(key) || "path".equals(key)) {
                    Optional<StringLiteralExpr> literalExpr = extractStringLiteral(pair.getValue());
                    if (literalExpr.isPresent()) {
                        return literalExpr;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<StringLiteralExpr> extractStringLiteral(Expression expression) {
        if (expression.isStringLiteralExpr()) {
            return Optional.of(expression.asStringLiteralExpr());
        }
        if (expression instanceof ArrayInitializerExpr initializerExpr) {
            for (Expression value : initializerExpr.getValues()) {
                if (value.isStringLiteralExpr()) {
                    return Optional.of(value.asStringLiteralExpr());
                }
            }
        }
        return Optional.empty();
    }

    private static String extractModuleName(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }

        String normalized = pathValue.trim().replace('\\', '/');
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return null;
        }

        String[] segments = normalized.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i].trim();
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.startsWith("{") && segment.endsWith("}")) {
                continue;
            }
            String cleaned = segment.replaceAll("[^A-Za-z0-9_-]", "");
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return null;
    }

    private static Replacement toNodeReplacement(ExtractionContext context, Range range, String placeholder) {
        int start = toOffset(context.getLineStartOffsets(), range.begin);
        int end = toOffset(context.getLineStartOffsets(), range.end) + 1;
        return new Replacement(start, end, placeholder);
    }

    private static Replacement buildModuleSegmentReplacement(
            ExtractionContext context, StringLiteralExpr literalExpr, String moduleName) {
        Optional<Range> rangeOptional = literalExpr.getRange();
        if (rangeOptional.isEmpty()) {
            return null;
        }

        Range range = rangeOptional.get();
        int start = toOffset(context.getLineStartOffsets(), range.begin);
        int end = toOffset(context.getLineStartOffsets(), range.end) + 1;
        if (start < 0 || end > context.getSource().length() || start >= end) {
            return null;
        }

        String literalRawText = context.getSource().substring(start, end);
        int moduleStartInLiteral = literalRawText.lastIndexOf(moduleName);
        if (moduleStartInLiteral < 0) {
            return null;
        }

        return new Replacement(
                start + moduleStartInLiteral,
                start + moduleStartInLiteral + moduleName.length(),
                MODULE_NAME_PLACEHOLDER);
    }

    private static final class AnchorVisitor extends VoidVisitorAdapter<ExtractionContext> {

        @Override
        public void visit(PackageDeclaration declaration, ExtractionContext context) {
            if (context.getPackageName() == null) {
                context.setPackageName(declaration.getNameAsString());
                declaration.getName().getRange()
                        .ifPresent(range -> context.addReplacement(toNodeReplacement(context, range, PACKAGE_NAME_PLACEHOLDER)));
            }
            super.visit(declaration, context);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration declaration, ExtractionContext context) {
            if (context.getClassName() == null) {
                boolean shouldUseCurrentClass = declaration.isTopLevelType() || !context.isFoundTopLevelClass();
                if (shouldUseCurrentClass) {
                    context.setClassName(stripClassSuffix(declaration.getNameAsString()));
                    declaration.getName().getRange()
                            .ifPresent(range -> context.addReplacement(toNodeReplacement(context, range, CLASS_NAME_PLACEHOLDER)));
                    if (declaration.isTopLevelType()) {
                        context.setFoundTopLevelClass(true);
                    }
                }
            }
            super.visit(declaration, context);
        }

        @Override
        public void visit(NormalAnnotationExpr annotationExpr, ExtractionContext context) {
            tryExtractModuleFromAnnotation(annotationExpr, context);
            super.visit(annotationExpr, context);
        }

        @Override
        public void visit(SingleMemberAnnotationExpr annotationExpr, ExtractionContext context) {
            tryExtractModuleFromAnnotation(annotationExpr, context);
            super.visit(annotationExpr, context);
        }

        @Override
        public void visit(MarkerAnnotationExpr annotationExpr, ExtractionContext context) {
            tryExtractModuleFromAnnotation(annotationExpr, context);
            super.visit(annotationExpr, context);
        }

        private void tryExtractModuleFromAnnotation(AnnotationExpr annotationExpr, ExtractionContext context) {
            if (context.getModuleName() != null || !isRouteAnnotation(annotationExpr)) {
                return;
            }
            extractPathLiteral(annotationExpr).ifPresent(literalExpr -> {
                String extractedModuleName = extractModuleName(literalExpr.getValue());
                if (extractedModuleName == null || extractedModuleName.isBlank()) {
                    return;
                }
                Replacement replacement = buildModuleSegmentReplacement(context, literalExpr, extractedModuleName);
                if (replacement != null) {
                    context.setModuleName(extractedModuleName);
                    context.addReplacement(replacement);
                }
            });
        }
    }

    private static final class ExtractionContext {
        private final String source;
        private final int[] lineStartOffsets;
        private final List<Replacement> replacements = new ArrayList<>();

        private boolean foundTopLevelClass;
        private String packageName;
        private String className;
        private String moduleName;

        private ExtractionContext(String source, int[] lineStartOffsets) {
            this.source = source;
            this.lineStartOffsets = lineStartOffsets;
        }

        private String getSource() {
            return source;
        }

        private int[] getLineStartOffsets() {
            return lineStartOffsets;
        }

        private List<Replacement> getReplacements() {
            return replacements;
        }

        private void addReplacement(Replacement replacement) {
            this.replacements.add(replacement);
        }

        private boolean isFoundTopLevelClass() {
            return foundTopLevelClass;
        }

        private void setFoundTopLevelClass(boolean foundTopLevelClass) {
            this.foundTopLevelClass = foundTopLevelClass;
        }

        private String getPackageName() {
            return packageName;
        }

        private void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        private String getClassName() {
            return className;
        }

        private void setClassName(String className) {
            this.className = className;
        }

        private String getModuleName() {
            return moduleName;
        }

        private void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }
    }

    private static final class Replacement {
        private final int start;
        private final int end;
        private final String value;

        private Replacement(int start, int end, String value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }

        private int getStart() {
            return start;
        }

        private int getEnd() {
            return end;
        }

        private String getValue() {
            return value;
        }
    }

    @Data
    public static class GenerateResult {
        private String ftlContent;
        private List<TemplateVariable> variables;
    }
}
