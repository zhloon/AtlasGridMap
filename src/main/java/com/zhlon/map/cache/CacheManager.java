package com.zhlon.map.cache;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 磁盘缓存清理管理器。
 *
 * <p>定期检查缓存目录大小，超出上限时按最后修改时间淘汰最旧的 .tile 文件。
 */
public class CacheManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path cacheRoot;

    public CacheManager(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    /** 清理超出限制的 Tile 文件，保留最新访问的条目 */
    public void evictByMaxBytes(long maxBytes) {
        try {
            if (!Files.exists(cacheRoot)) return;

            long totalSize = dirSize(cacheRoot);
            if (totalSize <= maxBytes) return;

            try (Stream<Path> files = Files.walk(cacheRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".tile"))
                        .sorted(Comparator.comparingLong(this::lastModified))
                        .forEach(file -> {
                            try {
                                long currentTotal = dirSize(cacheRoot);
                                if (currentTotal <= maxBytes) return;
                                long fileSize = Files.size(file);
                                Files.deleteIfExists(file);
                                LOGGER.debug("Evicted tile: {}", file.getFileName());
                            } catch (IOException e) {
                                LOGGER.warn("Failed to evict: {}", file, e);
                            }
                        });
            }
        } catch (IOException e) {
            LOGGER.error("Cache eviction failed", e);
        }
    }

    /** 获取指定服务器目录的总大小 */
    public long serverCacheSize(String serverHost) throws IOException {
        Path dir = cacheRoot.resolve(serverHost);
        if (!Files.exists(dir)) return 0;
        return dirSize(dir);
    }

    /** 清除指定维度的所有缓存 */
    public void clearDimension(String serverHost, String namespace, String path) throws IOException {
        Path dir = cacheRoot.resolve(serverHost).resolve(namespace).resolve(path);
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) {}
                        });
            }
        }
    }

    /** 清除所有缓存 */
    public void clearAll() throws IOException {
        if (Files.exists(cacheRoot)) {
            try (Stream<Path> files = Files.walk(cacheRoot)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) {}
                        });
            }
        }
    }

    private long dirSize(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); }
                        catch (IOException e) { return 0; }
                    })
                    .sum();
        }
    }

    private long lastModified(Path file) {
        try { return Files.getLastModifiedTime(file).toMillis(); }
        catch (IOException e) { return 0; }
    }
}
