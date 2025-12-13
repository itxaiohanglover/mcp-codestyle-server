package top.codestyle.mcp.plugin;


import lombok.Getter;

import top.codestyle.mcp.config.RepositoryConfigHolder;
import top.codestyle.mcp.service.CodestyleService;


/**
 * 非 Spring 环境下的手动组装（保证与 Spring 侧逻辑零重复）
 */
public final class PluginBootstrap {

    @Getter
    private final CodestyleService codestyleService;

    private static final class Holder {
        static final PluginBootstrap INSTANCE = new PluginBootstrap();
    }

    public static PluginBootstrap get() {
        return Holder.INSTANCE;
    }

    private PluginBootstrap() {
        this.codestyleService = new CodestyleService(
                RepositoryConfigHolder.isRemoteSearchEnabled(),
                RepositoryConfigHolder.getRepositoryDir(),
                RepositoryConfigHolder.getRemotePath());
    }

    public String getTemplateByPath(String path) throws Exception {
        return codestyleService.getTemplateByPath(path);
    }

    public String codestyleSearch(String keyword) {
        return codestyleService.codestyleSearch(keyword);
    }
}