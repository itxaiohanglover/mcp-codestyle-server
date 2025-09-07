package top.codestyle.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "project")
public class ProjectProperties {
    private String name;
    private String appName;
    private String version;
    private String description;
    private String url;
    private String basePackage;
    private Contact contact;
    private License license;
    private boolean production = false;

    @Data
    public static class Contact {
        private String name;
        private String email;
        private String url;
    }

    @Data
    public static class License {
        private String name;
        private String url;
    }
}
