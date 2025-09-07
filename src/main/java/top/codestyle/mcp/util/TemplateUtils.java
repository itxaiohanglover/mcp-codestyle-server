package top.codestyle.mcp.util;

import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;

import java.util.Map;

public class TemplateUtils {
    private static final String DEFAULT_TEMPLATE_PARENT_PATH = "templates";

    private TemplateUtils() {
    }

    public static String render(String templatePath, Map<?, ?> bindingMap) {
        return render("templates", templatePath, bindingMap);
    }

    public static String render(String parentPath, String templatePath, Map<?, ?> bindingMap) {
        TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig(parentPath, TemplateConfig.ResourceMode.CLASSPATH));
        Template template = engine.getTemplate(templatePath);
        return template.render(bindingMap);
    }
}
