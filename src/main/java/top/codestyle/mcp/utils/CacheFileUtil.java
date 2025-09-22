package top.codestyle.mcp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.codestyle.mcp.config.CacheConfig;
import top.codestyle.mcp.config.FileServerClient;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地缓存文件工具类
 */


@Slf4j
@Component
public class CacheFileUtil {

    @Autowired
    private CacheConfig cacheConfig;

    @Autowired
    private FileServerClient fileServerClient;

    @Autowired
    private LocalExistIndex existIndex;

    /**
     * 1. 先走内存 LRU
     * 2. 未命中再走磁盘 exists
     * 3. 磁盘结果回写 LRU
     */
    public boolean exists(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return false;
        }
        Boolean cached = existIndex.get(sha256);
        if (cached != null) {
            log.debug("LRU 命中 {}", sha256);
            return cached;
        }
        // 磁盘 IO 仅一次
        boolean onDisk = doDiskExists(sha256);
        existIndex.put(sha256, onDisk);
        return onDisk;
    }

    /**
     * 磁盘真实判断
     */
    private boolean doDiskExists(String sha256) {
        Path target = cacheConfig.cacheDirectory().resolve(sha256);
        return Files.exists(target);
    }

    /**
     * 兜底：本地不存在则去文件服务器拉取，并更新 LRU
     */
    public boolean ensureCached(String sha256) {
        if (exists(sha256)) {
            return true;
        }
        Path cacheFolder = cacheConfig.cacheDirectory();
        Path target = cacheFolder.resolve(sha256);
        boolean ok = fileServerClient.download(sha256, target);
        if (ok) {
            existIndex.put(sha256, Boolean.TRUE);   // 更新索引
        } else {
            // 服务器也没有，记住“不存在”防止反复下载
            existIndex.put(sha256, Boolean.FALSE);
        }
        return ok;
    }
}