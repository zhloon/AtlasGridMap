package com.zhlon.map.tile;

import com.mojang.logging.LogUtils;
import com.zhlon.map.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tile 核心管理器。
 *
 * <p>负责协调 Tile 的生成、存储、加载、脏标记和生命周期。
 * 单例模式，由主 Mod 类初始化。
 */
public final class TileManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static TileManager instance;

    private final TileGenerator generator;
    private final TileStorage storage;
    private String serverHost = "local";
    private final Set<TilePos> dirtyTiles;
    private final Set<TilePos> pendingTiles;
    private final Set<TilePos> visitedTiles;
    private final Map<TilePos, TileData> memoryCache;

    private TileManager(Path gameDir) {
        this.generator = new TileGenerator();
        this.storage = new TileStorage(gameDir);
        this.dirtyTiles = ConcurrentHashMap.newKeySet();
        this.pendingTiles = ConcurrentHashMap.newKeySet();
        this.visitedTiles = ConcurrentHashMap.newKeySet();
        this.memoryCache = new ConcurrentHashMap<>();
    }

    /** 初始化管理器 */
    public static void init(Path gameDir) {
        if (instance == null) {
            instance = new TileManager(gameDir);
            LOGGER.info("TileManager initialized, cache dir: {}", gameDir.resolve("atlasmap"));
        }
    }

    public static TileManager get() {
        if (instance == null) {
            throw new IllegalStateException("TileManager not initialized. Call init() first.");
        }
        return instance;
    }

    /** 设置服务器标识（用于多服务器缓存隔离） */
    public void setServerHost(String host) {
        this.serverHost = host;
    }

    /** 请求获取 Tile（优先缓存，未命中则加入待生成队列，不阻塞调用线程） */
    public CompletableFuture<TileData> requestTile(Level level, TilePos pos) {
        TileData cached = memoryCache.get(pos);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        Optional<TileData> fromDisk = storage.load(pos, serverHost);
        if (fromDisk.isPresent()) {
            TileData tile = fromDisk.get();
            memoryCache.put(pos, tile);
            return CompletableFuture.completedFuture(tile);
        }

        pendingTiles.add(pos);
        return CompletableFuture.completedFuture(null);
    }

    /** 标记 Tile 为脏（区块变更时调用） */
    public void markDirty(TilePos pos) {
        dirtyTiles.add(pos);
        pendingTiles.add(pos);
    }

    /** 标记区块变更影响的所有 Tile 为脏 */
    public void markChunkDirty(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        TilePos pos = TilePos.fromChunk(dimension, chunkX, chunkZ);
        markDirty(pos);
    }

    /** 标记 Tile 已被玩家探索 */
    public void markVisited(TilePos pos) {
        visitedTiles.add(pos);
        pendingTiles.add(pos);
    }

    /** 异步重新生成所有脏 / 待生成 Tile，完成后回调（可 null） */
    public void refreshDirtyTiles(Level level, Consumer<TileData> onRefresh) {
        Set<TilePos> toRefresh = new java.util.HashSet<>();
        toRefresh.addAll(dirtyTiles);
        toRefresh.addAll(pendingTiles);
        if (toRefresh.isEmpty()) return;

        for (TilePos pos : toRefresh) {
            generator.generate(level, pos).thenAccept(tile -> {
                if (tile == null) return;
                try {
                    storage.save(tile, serverHost);
                } catch (Exception e) {
                    LOGGER.error("Failed to save tile: {}", pos, e);
                }
                memoryCache.put(pos, tile);
                dirtyTiles.remove(pos);
                pendingTiles.remove(pos);
                if (onRefresh != null) {
                    onRefresh.accept(tile);
                }
            });
        }
    }

    /** 从服务端接收 Tile 数据（覆盖本地缓存） */
    public void receiveTile(TileData tile) {
        if (!tile.verifyIntegrity()) {
            LOGGER.warn("Received tile failed integrity check: {}", tile.pos());
            return;
        }

        try {
            storage.save(tile, serverHost);
        } catch (Exception e) {
            LOGGER.error("Failed to save received tile: {}", tile.pos(), e);
        }
        memoryCache.put(tile.pos(), tile);
    }

    /** 获取本地缓存的 Tile（不触发生成）。脏 / 未探索 Tile 返回空。 */
    public Optional<TileData> getCached(TilePos pos) {
        if (dirtyTiles.contains(pos)) {
            return Optional.empty();
        }
        if (Config.enableFogOfWar && !visitedTiles.contains(pos)) {
            return Optional.empty();
        }
        return getCachedSkipDirtyCheck(pos);
    }

    /** 跳过脏检查直接获取缓存（用于刷新后写入等内部场景） */
    private Optional<TileData> getCachedSkipDirtyCheck(TilePos pos) {
        TileData cached = memoryCache.get(pos);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<TileData> fromDisk = storage.load(pos, serverHost);
        fromDisk.ifPresent(t -> memoryCache.put(pos, t));
        return fromDisk;
    }

    /** 检查 Tile 是否在脏集合中 */
    public boolean isDirty(TilePos pos) {
        return dirtyTiles.contains(pos);
    }

    /** 检查 Tile 是否已被玩家探索 */
    public boolean isVisited(TilePos pos) {
        return visitedTiles.contains(pos);
    }

    /** 从内存缓存中移除指定 Tile（保留脏标记供 refreshDirtyTiles 处理） */
    public void evictCached(TilePos pos) {
        memoryCache.remove(pos);
    }

    /** 写入本地缓存（不触发持久化） */
    public void putCached(TileData tile) {
        memoryCache.put(tile.pos(), tile);
    }

    /** 获取当前内存中缓存的 Tile 数量 */
    public int getCachedCount() {
        return memoryCache.size();
    }

    public void shutdown() {
        generator.shutdown();
        memoryCache.clear();
        dirtyTiles.clear();
        pendingTiles.clear();
        visitedTiles.clear();
    }
}
