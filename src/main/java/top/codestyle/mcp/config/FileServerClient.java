package top.codestyle.mcp.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Component
public class FileServerClient {

    private final String baseUrl;
    private final int maxRetries;
    private final int bufferSize;
    private final HttpClient http;

    public FileServerClient(@Value("${file-server.base-url}") String baseUrl,
                            @Value("${file-server.max-retries:2}") int maxRetries,
                            @Value("${file-server.buffer-size:32768}") int bufferSize) {
        this.baseUrl = baseUrl;
        this.maxRetries = maxRetries;
        this.bufferSize = bufferSize;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 把文件服务器上的 sha256 文件下载到本地
     *
     * @param sha256     文件唯一标识
     * @param targetPath 本地目标路径（含文件名）
     * @return true=下载成功；false=服务器不存在或网络异常
     */
    public boolean download(String sha256, Path targetPath) {
        String url = baseUrl + "/" + sha256;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                // 把响应直接写入文件
                HttpResponse<Path> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofFile(targetPath));

                if (resp.statusCode() == 200) {
                    log.info("成功下载 {} -> {}", url, targetPath);
                    return true;
                }
                if (resp.statusCode() == 404) {
                    log.warn("服务器不存在文件 {}", sha256);
                    return false;
                }
                log.warn("下载 {} 返回异常状态码 {}", url, resp.statusCode());
            } catch (IOException | InterruptedException e) {
                log.warn("第 {} 次下载 {} 失败: {}", i + 1, url, e.getMessage());
            }
        }
        return false;
    }
}
