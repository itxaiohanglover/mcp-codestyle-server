package top.codestyle.mcp.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import top.codestyle.mcp.config.RepositoryConfigHolder;

/**
 * 搜索代码模板
 * @goal search
 */
@Mojo(name = "search", threadSafe = true)
public class SearchMojo extends AbstractMojo {

    @Parameter(property = "keyword", required = true)
    private String keyword;

    @Parameter(property = "remote", defaultValue = "false")
    private boolean remote;

    @Override
    public void execute() throws MojoExecutionException {
        RepositoryConfigHolder.manualSet(
                System.getProperty("java.io.tmpdir"),
                "https://your.repo",
                "",
                remote);
        String tree = PluginBootstrap.get().codestyleSearch(keyword);
        getLog().info("\n========== Template Directory Tree ==========\n" + tree);
    }
}