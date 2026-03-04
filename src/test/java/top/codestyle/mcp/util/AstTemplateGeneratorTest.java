package top.codestyle.mcp.util;

import org.junit.jupiter.api.Test;
import top.codestyle.mcp.model.template.TemplateVariable;

import static org.junit.jupiter.api.Assertions.*;

class AstTemplateGeneratorTest {

    @Test
    void testGenerateSimpleController() {
        String javaCode = """
                package com.example.demo.controller;

                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/user")
                public class UserController {

                    public String list() {
                        return "user list";
                    }
                }
                """;

        AstTemplateGenerator.GenerateResult result = AstTemplateGenerator.generate(javaCode);

        assertNotNull(result);
        assertNotNull(result.getFtlContent());
        assertNotNull(result.getVariables());

        // 验证模板内容包含占位符
        assertTrue(result.getFtlContent().contains("${packageName}"));
        assertTrue(result.getFtlContent().contains("${className}"));
        assertTrue(result.getFtlContent().contains("${moduleName}"));

        // 验证变量提取
        assertEquals(3, result.getVariables().size());

        TemplateVariable packageVar = result.getVariables().stream()
                .filter(v -> "packageName".equals(v.getVariableName()))
                .findFirst()
                .orElse(null);
        assertNotNull(packageVar);
        assertEquals("com.example.demo.controller", packageVar.getExample());

        TemplateVariable classVar = result.getVariables().stream()
                .filter(v -> "className".equals(v.getVariableName()))
                .findFirst()
                .orElse(null);
        assertNotNull(classVar);
        assertEquals("User", classVar.getExample()); // 剥离了 Controller 后缀

        TemplateVariable moduleVar = result.getVariables().stream()
                .filter(v -> "moduleName".equals(v.getVariableName()))
                .findFirst()
                .orElse(null);
        assertNotNull(moduleVar);
        assertEquals("user", moduleVar.getExample());

        System.out.println("=== 生成的模板内容 ===");
        System.out.println(result.getFtlContent());
        System.out.println("\n=== 提取的变量 ===");
        result.getVariables().forEach(v ->
            System.out.printf("%s: %s (示例: %s)%n",
                v.getVariableName(), v.getVariableComment(), v.getExample())
        );
    }

    @Test
    void testGenerateServiceImpl() {
        String javaCode = """
                package com.example.demo.service.impl;

                import org.springframework.stereotype.Service;

                @Service
                public class UserServiceImpl {

                    public void saveUser() {
                        // implementation
                    }
                }
                """;

        AstTemplateGenerator.GenerateResult result = AstTemplateGenerator.generate(javaCode);

        assertNotNull(result);
        assertTrue(result.getFtlContent().contains("${packageName}"));
        assertTrue(result.getFtlContent().contains("${className}"));

        // 验证类名剥离了 ServiceImpl 后缀
        TemplateVariable classVar = result.getVariables().stream()
                .filter(v -> "className".equals(v.getVariableName()))
                .findFirst()
                .orElse(null);
        assertNotNull(classVar);
        assertEquals("User", classVar.getExample());
    }

    @Test
    void testGenerateWithComplexMapping() {
        String javaCode = """
                package com.example.api;

                import org.springframework.web.bind.annotation.*;

                @RestController
                @RequestMapping("/api/v1/product")
                public class ProductController {

                    public String create() {
                        return "created";
                    }
                }
                """;

        AstTemplateGenerator.GenerateResult result = AstTemplateGenerator.generate(javaCode);

        // 验证提取最后一个有效路径段作为 moduleName
        TemplateVariable moduleVar = result.getVariables().stream()
                .filter(v -> "moduleName".equals(v.getVariableName()))
                .findFirst()
                .orElse(null);
        assertNotNull(moduleVar);
        assertEquals("product", moduleVar.getExample());
    }

    @Test
    void testGenerateEmptyCode() {
        assertThrows(IllegalArgumentException.class, () -> {
            AstTemplateGenerator.generate("");
        });
    }

    @Test
    void testGenerateInvalidCode() {
        String invalidCode = "this is not valid java code {{{";

        assertThrows(Exception.class, () -> {
            AstTemplateGenerator.generate(invalidCode);
        });
    }
}
