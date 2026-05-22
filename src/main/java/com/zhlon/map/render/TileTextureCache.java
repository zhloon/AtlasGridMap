package com.zhlon.map.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.zhlon.map.Config;
import com.zhlon.map.tile.TileData;
import com.zhlon.map.tile.TilePos;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GPU 纹理 LRU 缓存。
 *
 * <p>将 Tile 的 RGBA 数据转为 OpenGL DynamicTexture 并维护访问顺序，
 * 超出上限时淘汰最久未使用的纹理，释放显存。
 */
public class TileTextureCache {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<TilePos, DynamicTexture> cache;

    public TileTextureCache() {
        this.cache = new LinkedHashMap<TilePos, DynamicTexture>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<TilePos, DynamicTexture> eldest) {
                int max = Config.maxGpuTextures;
                if (max <= 0) return false;
                boolean shouldRemove = size() > max;
                if (shouldRemove) {
                    eldest.getValue().close();
                }
                return shouldRemove;
            }
        };
    }

    /** 从 TileData 创建 GPU 纹理并缓存 */
    public Optional<DynamicTexture> put(TilePos pos, TileData tile) {
        evict(pos);

        byte[] rgba = tile.compressedTexture();
        int texRes = Config.tileTextureResolution;
        int expectedLen = texRes * texRes * 4;

        if (rgba.length != expectedLen) {
            LOGGER.warn("Tile {} texture size mismatch: {} != {}", pos, rgba.length, expectedLen);
            return Optional.empty();
        }

        try (NativeImage image = new NativeImage(texRes, texRes, false)) {
            for (int y = 0; y < texRes; y++) {
                for (int x = 0; x < texRes; x++) {
                    int idx = (y * texRes + x) * 4;
                    int r = rgba[idx]     & 0xFF;
                    int g = rgba[idx + 1] & 0xFF;
                    int b = rgba[idx + 2] & 0xFF;
                    int a = rgba[idx + 3] & 0xFF;
                    image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            DynamicTexture texture = new DynamicTexture(image);
            cache.put(pos, texture);
            return Optional.of(texture);
        }
    }

    /** 获取 GPU 纹理 */
    public Optional<DynamicTexture> get(TilePos pos) {
        return Optional.ofNullable(cache.get(pos));
    }

    /** 移除指定纹理 */
    public void evict(TilePos pos) {
        DynamicTexture old = cache.remove(pos);
        if (old != null) {
            old.close();
        }
    }

    /** 清除全部纹理 */
    public void clear() {
        for (DynamicTexture tex : cache.values()) {
            tex.close();
        }
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
