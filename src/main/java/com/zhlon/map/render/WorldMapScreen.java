package com.zhlon.map.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.zhlon.map.Config;
import com.zhlon.map.tile.TileManager;
import com.zhlon.map.tile.TilePos;
import com.zhlon.map.waypoint.WaypointData;
import com.zhlon.map.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.List;

/**
 * 世界地图界面（M 键打开）。
 *
 * <p>支持鼠标拖动平移、滚轮缩放，流式加载当前视口内的 Tile。
 * 显示玩家标记、waypoint 标记和坐标。
 */
public class WorldMapScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final TileTextureCache textureCache;
    private int offsetX;
    private int offsetZ;
    private float zoomLevel;
    private static final float MIN_ZOOM = 0.125f;
    private static final float MAX_ZOOM = 4.0f;

    public WorldMapScreen() {
        super(Component.translatable("screen.atlasmap.world_map"));
        this.textureCache = new TileTextureCache();
        this.zoomLevel = 0.25f;
    }

    @Override
    protected void init() {
        var player = minecraft.player;
        if (player != null) {
            this.offsetX = (int) Math.floor(player.getX());
            this.offsetZ = (int) Math.floor(player.getZ());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int centerX = width / 2;
        int centerY = height / 2;
        int tileSize = Config.tileSizeBlocks;

        float scaledTileSize = tileSize * zoomLevel;
        int halfW = (int) (width / zoomLevel / tileSize) / 2 + 2;
        int halfH = (int) (height / zoomLevel / tileSize) / 2 + 2;

        int tileCX = Math.floorDiv(offsetX, tileSize);
        int tileCZ = Math.floorDiv(offsetZ, tileSize);

        Level level = minecraft.player != null ? minecraft.player.level() : null;
        if (level == null) return;

        for (int tz = tileCZ - halfH; tz <= tileCZ + halfH; tz++) {
            for (int tx = tileCX - halfW; tx <= tileCX + halfW; tx++) {
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

                int screenX = centerX + (int) ((tx * tileSize - offsetX) * zoomLevel);
                int screenY = centerY + (int) ((tz * tileSize - offsetZ) * zoomLevel);
                int drawSize = (int) Math.ceil(scaledTileSize) + 1;

                renderTile(tex, screenX, screenY, drawSize, drawSize);
            }
        }

        int playerScreenX = centerX;
        int playerScreenY = centerY;
        if (minecraft.player != null) {
            playerScreenX = centerX + (int) ((minecraft.player.getX() - offsetX) * zoomLevel);
            playerScreenY = centerY + (int) ((minecraft.player.getZ() - offsetZ) * zoomLevel);
        }

        renderPlayerMarker(guiGraphics, playerScreenX, playerScreenY);

        renderWaypoints(guiGraphics, level, centerX, centerY);

        String coords = String.format("(%d, %d, %d)  Zoom: %.0f%%  Tiles: %d  WPs: %d",
                offsetX, offsetZ,
                minecraft.player != null ? (int) minecraft.player.getY() : 0,
                zoomLevel * 100,
                TileManager.get().getCachedCount(),
                WaypointManager.get().count());
        guiGraphics.drawString(font, coords, 4, 4, 0xFFFFFF);
        guiGraphics.drawString(font, "ESC - Close | Arrows/WASD - Pan | Scroll - Zoom",
                4, height - 14, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** 渲染当前维度的所有可见 waypoint */
    private void renderWaypoints(GuiGraphics guiGraphics, Level level, int centerX, int centerY) {
        var player = minecraft.player;
        if (player == null) return;

        List<WaypointData> wps = WaypointManager.get().getVisibleFor(
                player.getUUID(), level.dimension());

        for (WaypointData wp : wps) {
            int wx = centerX + (int) ((wp.pos().getX() - offsetX) * zoomLevel);
            int wy = centerY + (int) ((wp.pos().getZ() - offsetZ) * zoomLevel);

            if (wx < 0 || wx > width || wy < 0 || wy > height) continue;

            int color = wp.color();
            int fillColor = 0xFF000000 | color;
            int borderColor = 0xFFFFFFFF;

            guiGraphics.fill(wx - 3, wy - 3, wx + 4, wy + 4, borderColor);
            guiGraphics.fill(wx - 2, wy - 2, wx + 3, wy + 3, fillColor);

            if (zoomLevel > 0.3f) {
                guiGraphics.drawString(font, wp.name(), wx + 6, wy - 4, 0xFFFFFF);
            }
        }
    }

    /** 使用 GuiGraphics 兼容方式渲染 Tile 纹理 */
    private void renderTile(DynamicTexture texture, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();

        Matrix4f matrix = new Matrix4f().translate(0, 0, 0);

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

    /** 绘制玩家位置标记 */
    private void renderPlayerMarker(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFFFFFF00);
        guiGraphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFF0000FF);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            offsetX -= (int) (dragX / zoomLevel);
            offsetZ -= (int) (dragY / zoomLevel);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        zoomLevel *= (delta > 0) ? 1.25f : 0.8f;
        zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int panSpeed = (int) (16 / zoomLevel);
        panSpeed = Math.max(1, panSpeed);

        return switch (keyCode) {
            case 87, 265 -> { offsetZ -= panSpeed; yield true; }
            case 83, 264 -> { offsetZ += panSpeed; yield true; }
            case 65, 263 -> { offsetX -= panSpeed; yield true; }
            case 68, 262 -> { offsetX += panSpeed; yield true; }
            default -> super.keyPressed(keyCode, scanCode, modifiers);
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        textureCache.clear();
    }
}
