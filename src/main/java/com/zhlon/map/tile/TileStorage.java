package com.zhlon.map.tile;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Tile 本地文件存储系统。
 *
 * <p>目录结构：
 * <pre>{@code
 * .minecraft/atlasmap/
 *  └── {server-host}/
 *      └── {dimension_namespace}/
 *          └── {dimension_path}/
 *              └── x_z.tile
 * }</pre>
 *
 * <p>文件格式（二进制）：
 * <pre>
 * [int version] [long updateTime] [int textureLen] [int maskLen]
 * [byte[] compressedTexture] [byte[] exploredMask] [byte[32] hash]
 * </pre>
 */
public class TileStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HEADER_SIZE = 4 + 8 + 4 + 4;
    private static final int HASH_SIZE = 32;

    private final Path basePath;

    public TileStorage(Path gameDir) {
        this.basePath = gameDir.resolve("atlasmap");
    }

    /** 保存 Tile 到本地文件 */
    public void save(TileData tile, String serverHost) throws IOException {
        Path file = tileFile(tile.pos(), serverHost);
        Files.createDirectories(file.getParent());

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(tile.version());
            out.writeLong(tile.updateTime());
            byte[] tex = tile.compressedTexture();
            out.writeInt(tex.length);
            out.writeInt(tile.exploredMask().length);
            out.write(tex);
            out.write(tile.exploredMask());
            out.write(tile.hash());
        }
    }

    /** 从本地文件加载 Tile */
    public Optional<TileData> load(TilePos pos, String serverHost) {
        Path file = tileFile(pos, serverHost);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            int version = in.readInt();
            long updateTime = in.readLong();
            int texLen = in.readInt();
            int maskLen = in.readInt();
            byte[] tex = new byte[texLen];
            in.readFully(tex);
            byte[] mask = new byte[maskLen];
            in.readFully(mask);
            byte[] hash = new byte[HASH_SIZE];
            in.readFully(hash);

            TileData tile = new TileData(pos, version, updateTime, tex, mask, hash);
            if (!tile.verifyIntegrity()) {
                LOGGER.warn("Tile integrity check failed: {}", pos);
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            return Optional.of(tile);
        } catch (IOException e) {
            LOGGER.error("Failed to load tile: {}", pos, e);
            return Optional.empty();
        }
    }

    /** 删除指定 Tile */
    public void delete(TilePos pos, String serverHost) {
        try {
            Files.deleteIfExists(tileFile(pos, serverHost));
        } catch (IOException e) {
            LOGGER.warn("Failed to delete tile: {}", pos, e);
        }
    }

    /** 检查指定 Tile 是否存在 */
    public boolean exists(TilePos pos, String serverHost) {
        return Files.exists(tileFile(pos, serverHost));
    }

    /** 计算缓存目录总大小（字节） */
    public long cacheSizeBytes(String serverHost) throws IOException {
        Path dir = basePath.resolve(serverHost);
        if (!Files.exists(dir)) return 0;
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); }
                        catch (IOException e) { return 0; }
                    })
                    .sum();
        }
    }

    /** 将维度 ResourceLocation 转为目录路径 */
    private String dimensionDir(TilePos pos) {
        ResourceLocation loc = pos.dimension().location();
        return loc.getNamespace() + "/" + loc.getPath();
    }

    private Path tileFile(TilePos pos, String serverHost) {
        String fileName = pos.x() + "_" + pos.z()
                + (pos.zoom() > 0 ? "@" + pos.zoom() : "") + ".tile";
        return basePath.resolve(serverHost).resolve(dimensionDir(pos)).resolve(fileName);
    }
}
