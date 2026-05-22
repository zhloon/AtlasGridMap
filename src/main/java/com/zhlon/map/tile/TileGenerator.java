package com.zhlon.map.tile;

import com.mojang.logging.LogUtils;
import com.zhlon.map.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tile 异步地图生成器。
 *
 * <p>扫描区块中的方块，生成 RGBA 纹理和探索遮罩。
 * 所有操作在独立线程池中执行，禁止阻塞主线程。
 *
 * <p>颜色算法：
 * <ol>
 *   <li>获取每列最高方块</li>
 *   <li>取方块 {@link MapColor} 主色</li>
 *   <li>叠加高度阴影</li>
 *   <li>叠加水体颜色</li>
 * </ol>
 */
public class TileGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TEXTURE_RES = 128;

    private final ExecutorService executor;

    public TileGenerator() {
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "AtlasMap-Generator");
                    t.setDaemon(true);
                    return t;
                });
    }

    /** 异步生成指定 Tile */
    public CompletableFuture<TileData> generate(Level level, TilePos pos) {
        return CompletableFuture.supplyAsync(() -> doGenerate(level, pos), executor);
    }

    private TileData doGenerate(Level level, TilePos pos) {
        int tileSize = Config.tileSizeBlocks;
        int res = TEXTURE_RES;
        int scale = tileSize / res;

        byte[] texture = new byte[res * res * 4];
        byte[] exploredMask = new byte[res * res];

        int minX = pos.minBlockX();
        int minZ = pos.minBlockZ();
        int worldTop = level.getMaxBuildHeight();
        int worldBottom = level.getMinBuildHeight();

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int texZ = 0; texZ < res; texZ++) {
            for (int texX = 0; texX < res; texX++) {
                int blockX = minX + texX * scale + scale / 2;
                int blockZ = minZ + texZ * scale + scale / 2;

                boolean explored = false;
                int topY = worldBottom;

                LevelChunk chunk = level.getChunk(blockX >> 4, blockZ >> 4);

                for (int y = worldTop - 1; y >= worldBottom; y--) {
                    mutablePos.set(blockX, y, blockZ);
                    BlockState state = chunk.getBlockState(mutablePos);

                    if (state.isAir()) continue;

                    topY = y;
                    explored = true;

                    int color = getBlockColor(state, level, mutablePos);

                    float heightFactor = (float)(y - worldBottom) / (worldTop - worldBottom);
                    color = applyHeightShade(color, heightFactor);

                    color = applyWaterBlend(color, state);

                    int idx = (texZ * res + texX) * 4;
                    texture[idx]     = (byte) ((color >> 16) & 0xFF);
                    texture[idx + 1] = (byte) ((color >> 8) & 0xFF);
                    texture[idx + 2] = (byte) (color & 0xFF);
                    texture[idx + 3] = (byte) 0xFF;

                    break;
                }

                exploredMask[texZ * res + texX] = (byte) (explored ? 1 : 0);
            }
        }

        byte[] compressed = compress(texture);
        long now = System.currentTimeMillis();

        return new TileData(pos, 1, now, compressed, exploredMask);
    }

    /** 获取方块在地图上的颜色 */
    private int getBlockColor(BlockState state, Level level, BlockPos pos) {
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor == MapColor.NONE) {
            mapColor = MapColor.STONE;
        }
        int mcColor = mapColor.col;
        int r = (mcColor >> 16) & 0xFF;
        int g = (mcColor >> 8) & 0xFF;
        int b = mcColor & 0xFF;
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    /** 高度阴影：越高越亮，越低越暗 */
    private int applyHeightShade(int color, float heightFactor) {
        float shade = 0.6f + heightFactor * 0.4f;
        int r = clamp((int) (((color >> 16) & 0xFF) * shade));
        int g = clamp((int) (((color >> 8) & 0xFF) * shade));
        int b = clamp((int) ((color & 0xFF) * shade));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    /** 水体颜色混合 */
    private int applyWaterBlend(int color, BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WATER) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            r = (r + 48) / 2;
            g = (g + 96) / 2;
            b = (b + 176) / 2;
            return (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
        return color;
    }

    /** 简易 RLE 压缩（占位实现，后续可替换为 ZSTD） */
    private byte[] compress(byte[] data) {
        return data;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    public void shutdown() {
        executor.shutdown();
    }
}
