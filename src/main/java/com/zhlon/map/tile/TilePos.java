package com.zhlon.map.tile;

import com.zhlon.map.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Tile 空间坐标。
 *
 * <p>一个 Tile 覆盖 {@code tileSize x tileSize} blocks 的区域。
 * 支持多维度、多缩放级别。
 */
public record TilePos(ResourceKey<Level> dimension, int x, int z, int zoom) {

    public TilePos(ResourceKey<Level> dimension, int x, int z) {
        this(dimension, x, z, 0);
    }

    /** 将方块坐标转换为 Tile 坐标（zoom=0） */
    public static TilePos fromBlock(ResourceKey<Level> dimension, int blockX, int blockZ) {
        int size = Config.tileSizeBlocks;
        return new TilePos(dimension,
                Math.floorDiv(blockX, size),
                Math.floorDiv(blockZ, size),
                0);
    }

    /** 将区块坐标转换为 Tile 坐标（zoom=0） */
    public static TilePos fromChunk(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return fromBlock(dimension, chunkX << 4, chunkZ << 4);
    }

    /** 该 Tile 左上角的方块 X 坐标 */
    public int minBlockX() {
        return x * Config.tileSizeBlocks;
    }

    /** 该 Tile 左上角的方块 Z 坐标 */
    public int minBlockZ() {
        return z * Config.tileSizeBlocks;
    }

    /** 该 Tile 右下角的方块 X 坐标（不包含） */
    public int maxBlockX() {
        return (x + 1) * Config.tileSizeBlocks;
    }

    /** 该 Tile 右下角的方块 Z 坐标（不包含） */
    public int maxBlockZ() {
        return (z + 1) * Config.tileSizeBlocks;
    }

    @Override
    public String toString() {
        return dimension.location() + "/" + x + "_" + z + (zoom > 0 ? "@" + zoom : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TilePos that)) return false;
        return x == that.x && z == that.z && zoom == that.zoom && dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        int result = dimension.hashCode();
        result = 31 * result + x;
        result = 31 * result + z;
        result = 31 * result + zoom;
        return result;
    }
}
