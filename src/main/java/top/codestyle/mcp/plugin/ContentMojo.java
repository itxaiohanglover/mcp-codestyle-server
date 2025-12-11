package top.codestyle.mcp.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import top.codestyle.mcp.config.RepositoryConfigHolder;

/**
 * 获取模板内容
 * @goal content
 */
@Mojo(name = "content", threadSafe = true)
public class ContentMojo extends AbstractMojo {

    @Parameter(property = "path", required = true)
    private String templatePath;

    @Parameter(property = "remote", defaultValue = "false")
    private boolean remote;

    @Override
    public void execute() throws MojoExecutionException {
        RepositoryConfigHolder.manualSet(
                System.getProperty("java.io.tmpdir"),
                "https://your.repo",
                "",
                remote);
        try {
            String detail = PluginBootstrap.get().getTemplateByPath(templatePath);
            getLog().info("\n========== Template Content ==========\n" + detail);
        } catch (Exception e) {
            throw new MojoExecutionException("Fetch content failed", e);
        }
    }
}