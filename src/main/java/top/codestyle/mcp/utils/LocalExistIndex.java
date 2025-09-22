package top.codestyle.mcp.utils;


import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.util.concurrent.ConcurrentMap;

/**
 * 纯内存 LRU 布隆替代品，value 存 Boolean(存在性)
 */
@Slf4j
@Component
public class LocalExistIndex {

    @Value("${cache.lru.max-size:10000}")
    private int maxSize;

    @Value("${cache.lru.concurrency-level:16}")
    private int concurrencyLevel;

    private ConcurrentMap<String, Boolean> index;

    @PostConstruct
    public void init() {
        index = new ConcurrentLinkedHashMap.Builder<String, Boolean>()
                .maximumWeightedCapacity(maxSize)
                .concurrencyLevel(concurrencyLevel)
                .build();
        log.info("本地 LRU 索引已初始化，容量={}", maxSize);
    }

    /**
     * 存在性缓存命中
     */
    public Boolean get(String sha256) {
        return index.get(sha256);
    }

    /**
     * 更新存在性
     */
    public void put(String sha256, boolean exist) {
        index.put(sha256, exist);
    }

    /**
     * 删除（极少用，预留）
     */
    public void remove(String sha256) {
        index.remove(sha256);
    }
}