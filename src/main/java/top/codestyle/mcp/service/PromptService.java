package top.codestyle.mcp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;

/**
 * 提示词模板加载服务
 * 使用懒加载模式从classpath读取提示词模板
 *
 * @author 小航love666, Kanttha, movclantian
 * @since 2025-09-29
 */
@Service
public class PromptService {

    private static final String CONTENT_RESULT_TEMPLATE_PATH = "classpath:content-result.txt";
    private static final String SEARCH_RESULT_TEMPLATE_PATH = "classpath:search-result.txt";

    @Autowired
    private ResourceLoader resourceLoader;

    private volatile String contentResultTemplate;
    private volatile String searchResultTemplate;

    /**
     * 线程安全懒加载模板内容模板
     */
    private String getContentResultTemplate() {
        if (contentResultTemplate == null) {
            synchronized (this) {
                if (contentResultTemplate == null) {
                    contentResultTemplate = loadTemplate(CONTENT_RESULT_TEMPLATE_PATH);
                }
            }
        }
        return contentResultTemplate;
    }

    /**
     * 线程安全懒加载搜索结果模板
     */
    private String getSearchResultTemplate() {
        if (searchResultTemplate == null) {
            synchronized (this) {
                if (searchResultTemplate == null) {
                    searchResultTemplate = loadTemplate(SEARCH_RESULT_TEMPLATE_PATH);
                }
            }
        }
        return searchResultTemplate;
    }

    /**
     * 从classpath加载模板文件
     *
     * @param templatePath 模板文件路径
     * @return 模板内容字符串
     * @throws IllegalStateException 文件不存在或加载失败
     */
    private String loadTemplate(String templatePath) {
        try {
            Resource resource = resourceLoader.getResource(templatePath);
            if (!resource.exists()) {
                throw new IllegalStateException("classpath 下找不到 " + templatePath);
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return content.strip();
        } catch (IOException e) {
            throw new IllegalStateException("加载 " + templatePath + " 失败", e);
        }
    }

    /**
     * 构建内容提示词(按顺序替换模板中的%{s}占位符)
     *
     * @param params 可变参数,依次对应模板中的%{s}
     * @return 替换后的提示词字符串
     * @throws IllegalArgumentException 参数数量与占位符数量不匹配
     */
    public String buildPrompt(String... params) {
        return buildFromTemplate(getContentResultTemplate(), params);
    }

    /**
     * 构建搜索结果(按顺序替换模板中的%{s}占位符)
     *
     * @param params 可变参数,依次对应模板中的%{s}
     * @return 替换后的搜索结果字符串
     * @throws IllegalArgumentException 参数数量与占位符数量不匹配
     */
    public String buildSearchResult(String... params) {
        return buildFromTemplate(getSearchResultTemplate(), params);
    }

    /**
     * 从模板构建内容
     *
     * @param template 模板内容
     * @param params   可变参数,依次对应模板中的%{s}
     * @return 替换后的字符串
     * @throws IllegalArgumentException 参数数量与占位符数量不匹配
     */
    private String buildFromTemplate(String template, String... params) {
        Objects.requireNonNull(params, "params must not be null");

        int placeholderCount = countPlaceholder(template);
        if (placeholderCount != params.length) {
            throw new IllegalArgumentException(
                    "模板需要 " + placeholderCount + " 个参数，实际传入 " + params.length);
        }

        String result = template;
        for (String p : params) {
            // 使用 Matcher.quoteReplacement 来转义特殊字符，避免 $ 和 \ 被当作正则表达式的反向引用
            String replacement = Matcher.quoteReplacement(p == null ? "" : p);
            result = result.replaceFirst("%\\{s}", replacement);
        }
        return result;
    }

    /**
     * 统计模板中%{s}占位符的数量
     */
    private static int countPlaceholder(String template) {
        int count = 0, idx = 0;
        while ((idx = template.indexOf("%{s}", idx)) != -1) {
            count++;
            idx += 4;
        }
        return count;
    }

}
