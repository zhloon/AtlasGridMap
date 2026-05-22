package com.zhlon.map.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.zhlon.map.Config;
import com.zhlon.map.tile.TileManager;
import com.zhlon.map.tile.TilePos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * HUD 小地图覆盖层。
 *
 * <p>在游戏画面右上角（可配置）渲染小地图，显示：
 * <ul>
 *   <li>玩家周围的 Tile 纹理</li>
 *   <li>玩家位置与朝向箭头</li>
 *   <li>战争迷雾遮罩</li>
 * </ul>
 */
public class MinimapOverlay {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final TileTextureCache textureCache;

    public MinimapOverlay() {
        this.textureCache = new TileTextureCache();
    }

    /** 在 HUD 层渲染小地图。由 Forge 事件调用。 */
    public void render(RenderGuiOverlayEvent.Post event) {
        if (!Config.enableMinimap) return;
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        int size = Config.minimapSize;
        int screenW = event.getWindow().getGuiScaledWidth();
        int screenH = event.getWindow().getGuiScaledHeight();

        int x, y;
        switch (Config.minimapPosition) {
            case TOP_LEFT:
                x = 4; y = 4;
                break;
            case BOTTOM_LEFT:
                x = 4; y = screenH - size - 4;
                break;
            case BOTTOM_RIGHT:
                x = screenW - size - 4; y = screenH - size - 4;
                break;
            case TOP_RIGHT:
            default:
                x = screenW - size - 4; y = 4;
                break;
        }

        float zoom = 1.0f / Config.minimapZoom;
        int playerBlockX = (int) Math.floor(player.getX());
        int playerBlockZ = (int) Math.floor(player.getZ());
        float playerYaw = player.getYRot();

        if (Config.minimapRotation) {
            renderRotated(player, x, y, size, zoom, playerBlockX, playerBlockZ, playerYaw);
        } else {
            renderFixed(player, x, y, size, zoom, playerBlockX, playerBlockZ);
        }
    }

    /** 固定方向渲染 */
    private void renderFixed(Player player, int x, int y, int size,
                              float zoom, int px, int pz) {
        drawBackground(x, y, size);

        int halfSize = size / 2;
        int centerX = x + halfSize;
        int centerY = y + halfSize;
        int tileSize = Config.tileSizeBlocks;
        int texRes = Config.tileTextureResolution;

        int blocksPerPixel = (int) (tileSize / (texRes * zoom));

        Level level = player.level();
        int tileMinX = Math.floorDiv(px - halfSize * blocksPerPixel, tileSize);
        int tileMaxX = Math.floorDiv(px + halfSize * blocksPerPixel, tileSize);
        int tileMinZ = Math.floorDiv(pz - halfSize * blocksPerPixel, tileSize);
        int tileMaxZ = Math.floorDiv(pz + halfSize * blocksPerPixel, tileSize);

        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                TilePos pos = new TilePos(level.dimension(), tx, tz);
                var tileOpt = TileManager.get().getCached(pos);
                if (tileOpt.isEmpty()) {
                    TileManager.get().requestTile(level, pos);
                    continue;
                }

                var tex = textureCache.get(pos)
                        .or(() -> textureCache.put(pos, tileOpt.get()))
                        .orElse(null);
                if (tex == null) continue;

                int tileScreenX = centerX + (int) ((tx * tileSize - px) / (float) blocksPerPixel);
                int tileScreenY = centerY + (int) ((tz * tileSize - pz) / (float) blocksPerPixel);
                int tileScreenSize = (int) (tileSize / (float) blocksPerPixel) + 1;

                renderTileTexture(tex, tileScreenX, tileScreenY, tileScreenSize, tileScreenSize);
            }
        }

        drawPlayerArrow(centerX, centerY, 0, 0xFFFFFF00);
    }

    /** 旋转方向渲染 */
    private void renderRotated(Player player, int x, int y, int size,
                                float zoom, int px, int pz, float yaw) {
        drawBackground(x, y, size);

        int halfSize = size / 2;
        int centerX = x + halfSize;
        int centerY = y + halfSize;
        int tileSize = Config.tileSizeBlocks;
        int texRes = Config.tileTextureResolution;
        int blocksPerPixel = (int) (tileSize / (texRes * zoom));

        Level level = player.level();
        int range = halfSize * blocksPerPixel;
        int tileMinX = Math.floorDiv(px - range, tileSize);
        int tileMaxX = Math.floorDiv(px + range, tileSize);
        int tileMinZ = Math.floorDiv(pz - range, tileSize);
        int tileMaxZ = Math.floorDiv(pz + range, tileSize);

        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 0);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(yaw + 180));

        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                TilePos pos = new TilePos(level.dimension(), tx, tz);
                var tileOpt = TileManager.get().getCached(pos);
                if (tileOpt.isEmpty()) {
                    TileManager.get().requestTile(level, pos);
                    continue;
                }

                var tex = textureCache.get(pos)
                        .or(() -> textureCache.put(pos, tileOpt.get()))
                        .orElse(null);
                if (tex == null) continue;

                int txOff = (int) ((tx * tileSize - px) / (float) blocksPerPixel);
                int tzOff = (int) ((tz * tileSize - pz) / (float) blocksPerPixel);
                int ts = (int) (tileSize / (float) blocksPerPixel) + 1;

                renderTileTexture(tex, txOff, tzOff, ts, ts);
            }
        }

        poseStack.popPose();

        drawPlayerArrow(centerX, centerY, yaw, 0xFFFFFF00);
    }

    /** 渲染单个 Tile 纹理 */
    private void renderTileTexture(DynamicTexture texture, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();

        Matrix4f matrix = new Matrix4f();
        matrix.translate(0, 0, 0);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(matrix, x, y + h, 0).uv(0, 1).endVertex();
        builder.vertex(matrix, x + w, y + h, 0).uv(1, 1).endVertex();
        builder.vertex(matrix, x + w, y, 0).uv(1, 0).endVertex();
        builder.vertex(matrix, x, y, 0).uv(0, 0).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
    }

    /** 绘制深色半透明背景 */
    private void drawBackground(int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = new Matrix4f();
        matrix.translate(0, 0, 0);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float a = 0.5f;
        builder.vertex(matrix, x, y + size, 0).color(0, 0, 0, a).endVertex();
        builder.vertex(matrix, x + size, y + size, 0).color(0, 0, 0, a).endVertex();
        builder.vertex(matrix, x + size, y, 0).color(0, 0, 0, a).endVertex();
        builder.vertex(matrix, x, y, 0).color(0, 0, 0, a).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
    }

    /** 绘制玩家箭头 */
    private void drawPlayerArrow(int cx, int cy, float angle, int color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();

        Matrix4f matrix = new Matrix4f();
        matrix.translate(0, 0, 0);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        double rad = Math.toRadians(angle);
        float tipX = cx + (float) Math.sin(rad) * 6;
        float tipY = cy - (float) Math.cos(rad) * 6;
        float lx1 = cx + (float) Math.sin(rad + 2.5) * 4;
        float ly1 = cy - (float) Math.cos(rad + 2.5) * 4;
        float lx2 = cx + (float) Math.sin(rad - 2.5) * 4;
        float ly2 = cy - (float) Math.cos(rad - 2.5) * 4;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, tipX, tipY, 0).color(r, g, b, 1f).endVertex();
        builder.vertex(matrix, lx1, ly1, 0).color(r, g, b, 1f).endVertex();
        builder.vertex(matrix, lx2, ly2, 0).color(r, g, b, 1f).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
    }

    public void clear() {
        textureCache.clear();
    }
}
