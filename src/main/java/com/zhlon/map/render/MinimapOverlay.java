package com.zhlon.map.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.zhlon.map.Config;
import com.zhlon.map.tile.TileData;
import com.zhlon.map.tile.TileManager;
import com.zhlon.map.tile.TilePos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Matrix4f IDENTITY = new Matrix4f();

    private final TileTextureCache textureCache;
    private final Map<TilePos, Long> tileDataVersions;
    private final Set<TilePos> requestedTiles;
    private int frameCounter;

    public MinimapOverlay() {
        this.textureCache = new TileTextureCache();
        this.tileDataVersions = new ConcurrentHashMap<>();
        this.requestedTiles = ConcurrentHashMap.newKeySet();
    }

    /** 在 HUD 层渲染小地图。由 Forge 事件调用。 */
    public void render(RenderGuiOverlayEvent.Post event) {
        if (!Config.enableMinimap) return;
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        frameCounter++;

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
            renderRotated(player, x, y, size, zoom, playerBlockX, playerBlockZ, playerYaw, event.getWindow());
        } else {
            renderFixed(player, x, y, size, zoom, playerBlockX, playerBlockZ, event.getWindow());
        }
        drawBearing(event.getGuiGraphics(), player, x, y, size);
        drawCoordinates(event.getGuiGraphics(), player, x, y, size);
    }

    /** 固定方向渲染 */
    private void renderFixed(Player player, int x, int y, int size,
                              float zoom, int px, int pz, Window window) {
        int tileSize = Config.tileSizeBlocks;
        int texRes = Config.tileTextureResolution;
        float scale = zoom * texRes / (float) tileSize;

        Level level = player.level();
        int halfSize = size / 2;
        int cx = x + halfSize;
        int cy = y + halfSize;
        float tileStepPx = tileSize * scale;
        int drawSize = Math.round(tileStepPx);

        int rangeBlocks = (int) (halfSize / scale);
        int tileMinX = Math.floorDiv(px - rangeBlocks, tileSize);
        int tileMaxX = Math.floorDiv(px + rangeBlocks, tileSize);
        int tileMinZ = Math.floorDiv(pz - rangeBlocks, tileSize);
        int tileMaxZ = Math.floorDiv(pz + rangeBlocks, tileSize);

        float firstTileX = cx + (tileMinX * tileSize - px) * scale;
        float firstTileZ = cy + (tileMinZ * tileSize - pz) * scale;
        int baseX = Math.round(firstTileX);
        int baseY = Math.round(firstTileZ);
        int cols = tileMaxX - tileMinX + 1;
        int rows = tileMaxZ - tileMinZ + 1;

        drawCircleBackground(x, y, size, IDENTITY);

        int guiScale = (int) window.getGuiScale();
        RenderSystem.enableScissor(
                x * guiScale,
                window.getHeight() - (y + size) * guiScale,
                size * guiScale,
                size * guiScale);

        for (int row = 0; row < rows; row++) {
            int tz = tileMinZ + row;
            int tileScrY = baseY + row * drawSize;
            for (int col = 0; col < cols; col++) {
                int tx = tileMinX + col;
                int tileScrX = baseX + col * drawSize;

                TilePos pos = new TilePos(level.dimension(), tx, tz);
                var tileMgr = TileManager.get();
                var tileOpt = tileMgr.getCached(pos);
                if (tileOpt.isEmpty()) {
                    requestTileOnce(level, pos);
                    continue;
                }
                var tex = getOrUploadTexture(pos, tileOpt.get());
                if (tex == null) continue;

                renderTileTexture(tex, tileScrX, tileScrY, drawSize, drawSize, IDENTITY);
            }
        }

        if (Config.showPlayersMinimap && frameCounter % 4 == 0) {
            drawOtherPlayerDots(level, cx, cy, scale, px, pz, IDENTITY);
        }

        if (Config.showEntitiesMinimap && frameCounter % 10 == 0) {
            drawEntityDots(level, cx, cy, scale, px, pz, rangeBlocks, IDENTITY);
        }

        RenderSystem.disableScissor();
        drawCircleFrame(x, y, size, IDENTITY);
        drawPlayerArrow(cx, cy, player.getYRot() + 180, 0xFFFF0000, IDENTITY);
    }

    /** 节流请求 Tile */
    private void requestTileOnce(Level level, TilePos pos) {
        if (!requestedTiles.add(pos)) return;
        if (requestedTiles.size() > 16 && frameCounter % 8 != 0) return;
        TileManager.get().requestTile(level, pos);
    }

    /** 获取或上传 GPU 纹理，用 updateTime 对比避免每帧重复上传 */
    private DynamicTexture getOrUploadTexture(TilePos pos, TileData tileData) {
        long version = tileData.updateTime();
        Long cachedVersion = tileDataVersions.get(pos);
        if (cachedVersion == null || cachedVersion != version) {
            textureCache.evict(pos);
        }
        var tex = textureCache.get(pos)
                .or(() -> textureCache.put(pos, tileData))
                .orElse(null);
        if (tex != null) {
            tileDataVersions.put(pos, version);
        }
        return tex;
    }

    /** 旋转方向渲染（PoseStack 整体旋转坐标系，tile 保持网格对齐） */
    private void renderRotated(Player player, int x, int y, int size,
                                float zoom, int px, int pz, float yaw, Window window) {
        int tileSize = Config.tileSizeBlocks;
        int texRes = Config.tileTextureResolution;
        float scale = zoom * texRes / (float) tileSize;

        Level level = player.level();
        int halfSize = size / 2;
        int cx = x + halfSize;
        int cy = y + halfSize;
        int drawSize = Math.round(tileSize * scale);

        int rangeBlocks = (int) (halfSize / scale);
        int tileMinX = Math.floorDiv(px - rangeBlocks, tileSize);
        int tileMaxX = Math.floorDiv(px + rangeBlocks, tileSize);
        int tileMinZ = Math.floorDiv(pz - rangeBlocks, tileSize);
        int tileMaxZ = Math.floorDiv(pz + rangeBlocks, tileSize);

        float firstTileX = cx + (tileMinX * tileSize - px) * scale;
        float firstTileZ = cy + (tileMinZ * tileSize - pz) * scale;
        int baseX = Math.round(firstTileX);
        int baseY = Math.round(firstTileZ);
        int cols = tileMaxX - tileMinX + 1;
        int rows = tileMaxZ - tileMinZ + 1;

        int guiScale = (int) window.getGuiScale();
        RenderSystem.enableScissor(
                x * guiScale,
                window.getHeight() - (y + size) * guiScale,
                size * guiScale,
                size * guiScale);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(cx, cy, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(yaw));
        poseStack.translate(-cx, -cy, 0);
        Matrix4f rotMatrix = poseStack.last().pose();

        drawCircleBackground(x, y, size, rotMatrix);

        for (int row = 0; row < rows; row++) {
            int tz = tileMinZ + row;
            int tileScrY = baseY + row * drawSize;
            for (int col = 0; col < cols; col++) {
                int tx = tileMinX + col;
                int tileScrX = baseX + col * drawSize;

                TilePos pos = new TilePos(level.dimension(), tx, tz);
                var tileMgr = TileManager.get();
                var tileOpt = tileMgr.getCached(pos);
                if (tileOpt.isEmpty()) {
                    requestTileOnce(level, pos);
                    continue;
                }
                var tex = getOrUploadTexture(pos, tileOpt.get());
                if (tex == null) continue;

                renderTileTexture(tex, tileScrX, tileScrY, drawSize, drawSize, rotMatrix);
            }
        }

        if (Config.showPlayersMinimap && frameCounter % 4 == 0) {
            drawOtherPlayerDots(level, cx, cy, scale, px, pz, rotMatrix);
        }

        if (Config.showEntitiesMinimap && frameCounter % 10 == 0) {
            drawEntityDots(level, cx, cy, scale, px, pz, rangeBlocks, rotMatrix);
        }

        RenderSystem.disableScissor();
        drawCircleFrame(x, y, size, IDENTITY);
        drawPlayerArrow(cx, cy, 0, 0xFFFF0000, IDENTITY);
    }

    /** 渲染单个 Tile 纹理 */
    private void renderTileTexture(DynamicTexture texture, int x, int y, int w, int h, Matrix4f matrix) {
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

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

    /** 绘制深色半透明圆形背景 */
    private void drawCircleBackground(int x, int y, int size, Matrix4f matrix) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        float r = size / 2f;
        int segments = 48;

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, cx, cy, 0).color(0, 0, 0, 0.55f).endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            builder.vertex(matrix, cx + cos * r, cy + sin * r, 0).color(0, 0, 0, 0.55f).endVertex();
        }
        tesselator.end();

        RenderSystem.disableBlend();
    }

    /** 绘制圆形遮罩：覆盖圆外四角 + 细圆环边框 */
    private void drawCircleFrame(int x, int y, int size, Matrix4f matrix) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        float r = size / 2f;
        int segs = 16;

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        float[][] cornerDefs = {
            {x, y, (float) Math.PI},
            {x + size, y, (float) (3 * Math.PI / 2)},
            {x + size, y + size, 0f},
            {x, y + size, (float) (Math.PI / 2)},
        };

        for (float[] def : cornerDefs) {
            float cornerX = def[0];
            float cornerY = def[1];
            float startAngle = def[2];
            builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            builder.vertex(matrix, cornerX, cornerY, 0).color(0f, 0f, 0f, 1f).endVertex();
            for (int i = 0; i <= segs; i++) {
                double angle = startAngle + (Math.PI / 2.0) * i / segs;
                float px = cx + (float) Math.cos(angle) * r;
                float py = cy + (float) Math.sin(angle) * r;
                builder.vertex(matrix, px, py, 0).color(0f, 0f, 0f, 1f).endVertex();
            }
            tesselator.end();
        }

        float borderInner = r - 1;
        builder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int totalSegs = 64;
        for (int i = 0; i <= totalSegs; i++) {
            double angle = 2.0 * Math.PI * i / totalSegs;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            builder.vertex(matrix, cx + cos * r, cy + sin * r, 0).color(0.3f, 0.3f, 0.3f, 1f).endVertex();
            builder.vertex(matrix, cx + cos * borderInner, cy + sin * borderInner, 0).color(0.3f, 0.3f, 0.3f, 0f).endVertex();
        }
        tesselator.end();

        RenderSystem.disableBlend();
    }

    /** 绘制玩家红色实心箭头（黑色描边，指向镜头方向） */
    private void drawPlayerArrow(int cx, int cy, float angle, int color, Matrix4f matrix) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        double rad = Math.toRadians(angle);
        float tipX = cx + (float) Math.sin(rad) * 10;
        float tipY = cy - (float) Math.cos(rad) * 10;
        float lx1 = cx + (float) Math.sin(rad + 2.5) * 7;
        float ly1 = cy - (float) Math.cos(rad + 2.5) * 7;
        float lx2 = cx + (float) Math.sin(rad - 2.5) * 7;
        float ly2 = cy - (float) Math.cos(rad - 2.5) * 7;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        float outlineTipX = cx + (float) Math.sin(rad) * 11;
        float outlineTipY = cy - (float) Math.cos(rad) * 11;
        float outlineLx1 = cx + (float) Math.sin(rad + 2.8) * 8;
        float outlineLy1 = cy - (float) Math.cos(rad + 2.8) * 8;
        float outlineLx2 = cx + (float) Math.sin(rad - 2.8) * 8;
        float outlineLy2 = cy - (float) Math.cos(rad - 2.8) * 8;

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, outlineTipX, outlineTipY, 0).color(0, 0, 0, 1f).endVertex();
        builder.vertex(matrix, outlineLx1, outlineLy1, 0).color(0, 0, 0, 1f).endVertex();
        builder.vertex(matrix, outlineLx2, outlineLy2, 0).color(0, 0, 0, 1f).endVertex();
        tesselator.end();

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, tipX, tipY, 0).color(r, g, b, 1f).endVertex();
        builder.vertex(matrix, lx1, ly1, 0).color(r, g, b, 1f).endVertex();
        builder.vertex(matrix, lx2, ly2, 0).color(r, g, b, 1f).endVertex();
        tesselator.end();

        RenderSystem.disableBlend();
    }

    /** 绘制方位文字 N/S/E/W */
    private void drawBearing(GuiGraphics gfx, Player player, int x, int y, int size) {
        int halfSize = size / 2;
        int cx = x + halfSize;
        int cy = y + halfSize;
        var font = Minecraft.getInstance().font;
        int color = 0xCCFFFFFF;

        if (Config.minimapRotation) {
            float yaw = player.getYRot();
            double rad = Math.toRadians(-yaw);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            int pad = halfSize - 8;

            int nX = cx + (int) (sin * pad);
            int nY = cy - (int) (cos * pad);
            gfx.drawCenteredString(font, "N", nX, nY - 4, color);

            int sX = cx - (int) (sin * pad);
            int sY = cy + (int) (cos * pad);
            gfx.drawCenteredString(font, "S", sX, sY - 4, color);

            int eX = cx + (int) (cos * pad);
            int eY = cy + (int) (sin * pad);
            gfx.drawCenteredString(font, "E", eX, eY - 4, color);

            int wX = cx - (int) (cos * pad);
            int wY = cy - (int) (sin * pad);
            gfx.drawCenteredString(font, "W", wX, wY - 4, color);
        } else {
            gfx.drawCenteredString(font, "N", cx, y + 2, color);
            gfx.drawCenteredString(font, "S", cx, y + size - 12, color);
            gfx.drawCenteredString(font, "E", x + size - 10, cy - 4, color);
            gfx.drawCenteredString(font, "W", x + 10, cy - 4, color);
        }
    }

    /** 在 minimap 下方绘制玩家坐标 */
    private void drawCoordinates(GuiGraphics gfx, Player player, int x, int y, int size) {
        var font = Minecraft.getInstance().font;
        int textY = y + size + 2;
        int centerX = x + size / 2;
        String coords = String.format("%d, %d, %d",
                (int) Math.floor(player.getX()),
                (int) Math.floor(player.getY()),
                (int) Math.floor(player.getZ()));
        gfx.drawCenteredString(font, coords, centerX, textY, 0xCCFFFFFF);
    }

    /** 绘制其他玩家位置（小色点） */
    private void drawOtherPlayerDots(Level level, int cx, int cy, float scale,
                                      int px, int pz, Matrix4f matrix) {
        var self = Minecraft.getInstance().player;
        if (self == null) return;

        for (Player other : level.players()) {
            if (other.getUUID().equals(self.getUUID())) continue;

            int dotX = (int) Math.round(cx + (other.getX() - px) * scale);
            int dotY = (int) Math.round(cy + (other.getZ() - pz) * scale);

            int color = 0xFF000000 | ((other.getUUID().hashCode() & 0xFFFFFF) | 0x444444);

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            int s = 3;
            builder.vertex(matrix, dotX - s, dotY + s, 0).color(r, g, b, 1).endVertex();
            builder.vertex(matrix, dotX + s, dotY + s, 0).color(r, g, b, 1).endVertex();
            builder.vertex(matrix, dotX + s, dotY - s, 0).color(r, g, b, 1).endVertex();
            builder.vertex(matrix, dotX - s, dotY - s, 0).color(r, g, b, 1).endVertex();
            tesselator.end();

            RenderSystem.disableBlend();
        }
    }

    /** 绘制周围实体位置（敌对红色、友好绿色） */
    private void drawEntityDots(Level level, int cx, int cy, float scale,
                                 int px, int pz, int rangeBlocks, Matrix4f matrix) {
        var aabb = new AABB(px - rangeBlocks, -64, pz - rangeBlocks,
                px + rangeBlocks, 320, pz + rangeBlocks);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, aabb, e -> !(e instanceof Player))) {
            int dotX = (int) Math.round(cx + (entity.getX() - px) * scale);
            int dotY = (int) Math.round(cy + (entity.getZ() - pz) * scale);

            boolean friendly = entity.getType().getCategory().isFriendly();
            int argb = friendly ? 0xFF00FF00 : 0xFFFF0000;

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;
            int s = 2;
            builder.vertex(matrix, dotX - s, dotY + s, 0).color(r, g, b, 1).endVertex();
            builder.vertex(matrix, dotX + s, dotY + s, 0).color(r, g, b, 1).endVertex();
            builder.vertex(matrix, dotX + s, dotY - s, 0).color(r, g, b, 1).endVertex();
            builder.vertex(matrix, dotX - s, dotY - s, 0).color(r, g, b, 1).endVertex();
            tesselator.end();

            RenderSystem.disableBlend();
        }
    }

    public void clear() {
        textureCache.clear();
        tileDataVersions.clear();
        requestedTiles.clear();
        frameCounter = 0;
    }
}
