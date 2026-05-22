package com.zhlon.map.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.zhlon.map.Config;
import com.zhlon.map.tile.TileManager;
import com.zhlon.map.tile.TilePos;
import com.zhlon.map.waypoint.WaypointData;
import com.zhlon.map.waypoint.WaypointManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private final Matrix4f tileMatrix = new Matrix4f().translate(0, 0, 0);
    private final Set<TilePos> requestedTiles = ConcurrentHashMap.newKeySet();
    private int frameCount;

    private boolean showContextMenu;
    private int contextMenuX;
    private int contextMenuY;
    private int contextWorldX;
    private int contextWorldY;
    private int contextWorldZ;

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
        frameCount++;

        int centerX = width / 2;
        int centerY = height / 2;
        int tileSize = Config.tileSizeBlocks;

        int drawSize = (int) Math.ceil(tileSize * zoomLevel) + 1;
        int halfW = (int) (width / zoomLevel / tileSize) / 2 + 2;
        int halfH = (int) (height / zoomLevel / tileSize) / 2 + 2;

        int tileCX = Math.floorDiv(offsetX, tileSize);
        int tileCZ = Math.floorDiv(offsetZ, tileSize);

        Level level = minecraft.player != null ? minecraft.player.level() : null;
        if (level == null) return;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();

        int tilesRendered = 0;
        int maxTiles = 300;

        for (int tz = tileCZ - halfH; tz <= tileCZ + halfH && tilesRendered < maxTiles; tz++) {
            for (int tx = tileCX - halfW; tx <= tileCX + halfW && tilesRendered < maxTiles; tx++) {
                int screenX = centerX + (int) ((tx * tileSize - offsetX) * zoomLevel);
                int screenY = centerY + (int) ((tz * tileSize - offsetZ) * zoomLevel);

                if (screenX + drawSize < 0 || screenX > width
                        || screenY + drawSize < 0 || screenY > height) {
                    continue;
                }

                TilePos pos = new TilePos(level.dimension(), tx, tz);
                var tileOpt = TileManager.get().getCached(pos);
                if (tileOpt.isEmpty()) {
                    if (requestedTiles.add(pos) || frameCount % 10 == 0) {
                        TileManager.get().requestTile(level, pos);
                    }
                    continue;
                }

                var tex = textureCache.get(pos)
                        .or(() -> textureCache.put(pos, tileOpt.get()))
                        .orElse(null);
                if (tex == null) continue;

                renderTileFast(tex, screenX, screenY, drawSize);
                tilesRendered++;
            }
        }

        RenderSystem.disableBlend();

        int playerScreenX = centerX;
        int playerScreenY = centerY;
        if (minecraft.player != null) {
            playerScreenX = centerX + (int) ((minecraft.player.getX() - offsetX) * zoomLevel);
            playerScreenY = centerY + (int) ((minecraft.player.getZ() - offsetZ) * zoomLevel);
        }

        renderPlayerMarker(guiGraphics, playerScreenX, playerScreenY, minecraft.player.getYRot());

        renderWaypoints(guiGraphics, level, centerX, centerY);

        if (Config.showPlayersMinimap) {
            renderOtherPlayers(guiGraphics, level, centerX, centerY);
        }

        int playerX = minecraft.player != null ? (int) Math.floor(minecraft.player.getX()) : 0;
        int playerY = minecraft.player != null ? (int) Math.floor(minecraft.player.getY()) : 0;
        int playerZ = minecraft.player != null ? (int) Math.floor(minecraft.player.getZ()) : 0;
        String coords = String.format("(%d, %d, %d)  Zoom: %.0f%%",
                playerX, playerY, playerZ,
                zoomLevel * 100);
        guiGraphics.drawString(font, coords, 4, 4, 0xFFFFFF);

        int mouseWorldX = offsetX + (int) ((mouseX - centerX) / zoomLevel);
        int mouseWorldZ = offsetZ + (int) ((mouseY - centerY) / zoomLevel);
        String mouseCoords = String.format("Cursor: (%d, %d)", mouseWorldX, mouseWorldZ);
        guiGraphics.drawString(font, mouseCoords, 4, 14, 0xCCCCCC);

        guiGraphics.drawString(font, Component.translatable("screen.atlasmap.help"),
                4, height - 14, 0xAAAAAA);

        if (showContextMenu) {
            renderContextMenu(guiGraphics);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderContextMenu(GuiGraphics gfx) {
        int menuW = 150;
        int menuH = 20;
        int mx = contextMenuX;
        int my = contextMenuY;

        if (mx + menuW > width) mx = width - menuW;
        if (my + menuH > height) my = height - menuH;

        boolean explored = isTileVisited(contextWorldX, contextWorldZ);
        int bg = explored ? 0xCC000000 : 0xCC440000;

        gfx.fill(mx, my, mx + menuW, my + menuH, bg);
        if (explored) {
            gfx.drawString(font, Component.translatable("screen.atlasmap.teleport_to",
                    contextWorldX, contextWorldY, contextWorldZ),
                    mx + 4, my + 5, 0xFFFFFF);
        } else {
            gfx.drawString(font, Component.translatable("screen.atlasmap.locked",
                    contextWorldX, contextWorldZ),
                    mx + 4, my + 5, 0xFF8888);
        }
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

    /** 渲染 Tile 纹理（不设置 shader/blend，由外层统一管理） */
    private void renderTileFast(DynamicTexture texture, int x, int y, int size) {
        RenderSystem.setShaderTexture(0, texture.getId());
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(tileMatrix, x, y + size, 0).uv(0, 1).endVertex();
        builder.vertex(tileMatrix, x + size, y + size, 0).uv(1, 1).endVertex();
        builder.vertex(tileMatrix, x + size, y, 0).uv(1, 0).endVertex();
        builder.vertex(tileMatrix, x, y, 0).uv(0, 0).endVertex();
        tesselator.end();
    }

    /** 绘制玩家红色实心三角箭头（黑色描边，指向镜头方向） */
    private void renderPlayerMarker(GuiGraphics gfx, int x, int y, float yaw) {
        double rad = Math.toRadians(yaw + 180);
        int tipX = x + (int) (Math.sin(rad) * 12);
        int tipY = y - (int) (Math.cos(rad) * 12);
        int leftX = x + (int) (Math.sin(rad + 2.5) * 8);
        int leftY = y - (int) (Math.cos(rad + 2.5) * 8);
        int rightX = x + (int) (Math.sin(rad - 2.5) * 8);
        int rightY = y - (int) (Math.cos(rad - 2.5) * 8);

        int oTipX = x + (int) (Math.sin(rad) * 14);
        int oTipY = y - (int) (Math.cos(rad) * 14);
        int oLeftX = x + (int) (Math.sin(rad + 3.0) * 9);
        int oLeftY = y - (int) (Math.cos(rad + 3.0) * 9);
        int oRightX = x + (int) (Math.sin(rad - 3.0) * 9);
        int oRightY = y - (int) (Math.cos(rad - 3.0) * 9);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(tileMatrix, oTipX, oTipY, 0).color(0, 0, 0, 1).endVertex();
        builder.vertex(tileMatrix, oLeftX, oLeftY, 0).color(0, 0, 0, 1).endVertex();
        builder.vertex(tileMatrix, oRightX, oRightY, 0).color(0, 0, 0, 1).endVertex();
        tesselator.end();

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(tileMatrix, tipX, tipY, 0).color(1, 0, 0, 1).endVertex();
        builder.vertex(tileMatrix, leftX, leftY, 0).color(1, 0, 0, 1).endVertex();
        builder.vertex(tileMatrix, rightX, rightY, 0).color(1, 0, 0, 1).endVertex();
        tesselator.end();
        RenderSystem.disableBlend();
    }

    /** 渲染其他在线玩家头像 */
    private void renderOtherPlayers(GuiGraphics gfx, Level level, int centerX, int centerY) {
        var player = minecraft.player;
        if (player == null) return;

        UUID selfId = player.getUUID();

        for (Player other : level.players()) {
            if (other.getUUID().equals(selfId)) continue;

            int px = centerX + (int) ((other.getX() - offsetX) * zoomLevel);
            int py = centerY + (int) ((other.getZ() - offsetZ) * zoomLevel);

            if (px < -16 || px > width + 16 || py < -16 || py > height + 16) continue;

            int headSize = 10;
            renderPlayerHead(gfx, other.getUUID(), other.getGameProfile().getName(),
                    px - headSize / 2, py - headSize / 2, headSize);
        }
    }

    /** 渲染单个玩家头像 */
    private void renderPlayerHead(GuiGraphics gfx, UUID uuid, String name, int x, int y, int size) {
        PlayerInfo info = minecraft.getConnection() != null
                ? minecraft.getConnection().getPlayerInfo(uuid) : null;
        ResourceLocation skinLoc = info != null
                ? minecraft.getSkinManager().getInsecureSkinLocation(info.getProfile())
                : null;

        if (skinLoc != null) {
            RenderSystem.setShaderTexture(0, skinLoc);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();
            var tesselator = Tesselator.getInstance();
            var builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float u0 = 8f / 64f;
            float v0 = 8f / 64f;
            float u1 = 16f / 64f;
            float v1 = 16f / 64f;
            builder.vertex(tileMatrix, x, y + size, 0).uv(u0, v1).endVertex();
            builder.vertex(tileMatrix, x + size, y + size, 0).uv(u1, v1).endVertex();
            builder.vertex(tileMatrix, x + size, y, 0).uv(u1, v0).endVertex();
            builder.vertex(tileMatrix, x, y, 0).uv(u0, v0).endVertex();
            tesselator.end();
            RenderSystem.disableBlend();
        } else {
            int color = 0xFF000000 | ((uuid.hashCode() & 0xFFFFFF) | 0x666666);
            gfx.fill(x, y, x + size, y + size, 0xFFFFFFFF);
            gfx.fill(x + 1, y + 1, x + size - 1, y + size - 1, color);
        }

        gfx.drawCenteredString(font, name, x + size / 2, y + size + 2, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showContextMenu) {
            int mx = contextMenuX;
            int my = contextMenuY;
            int menuW = 150;
            int menuH = 20;
            if (mx + menuW > width) mx = width - menuW;
            if (my + menuH > height) my = height - menuH;

            if (mouseX >= mx && mouseX <= mx + menuW && mouseY >= my && mouseY <= my + menuH) {
                if (isTileVisited(contextWorldX, contextWorldZ)) {
                    doTeleport();
                }
                showContextMenu = false;
                return true;
            }

            showContextMenu = false;
            return true;
        }

        if (button == 1) {
            int centerX = width / 2;
            int centerY = height / 2;
            contextWorldX = offsetX + (int) ((mouseX - centerX) / zoomLevel);
            contextWorldZ = offsetZ + (int) ((mouseY - centerY) / zoomLevel);
            contextWorldY = getTargetY(contextWorldX, contextWorldZ);
            contextMenuX = (int) mouseX;
            contextMenuY = (int) mouseY;
            showContextMenu = true;
            return true;
        }

        if (button == 0) {
            showContextMenu = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void doTeleport() {
        if (minecraft.player == null) return;
        minecraft.player.connection.sendUnsignedCommand(
                "tp @s " + contextWorldX + " " + contextWorldY + " " + contextWorldZ);
    }

    /** 查询目标坐标的安全传送 Y（至少 2 格空气，避免卡墙） */
    private int getTargetY(int x, int z) {
        if (minecraft.player == null) return 64;
        var level = minecraft.player.level();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        for (int searchY = y; searchY < maxY; searchY++) {
            if (level.getBlockState(
                    new BlockPos(x, searchY, z)).isAir()
                    && level.getBlockState(
                    new BlockPos(x, searchY + 1, z)).isAir()) {
                return searchY;
            }
        }
        return Math.min(y, maxY);
    }

    /** 检查目标坐标所在 Tile 是否已被玩家探索 */
    private boolean isTileVisited(int worldX, int worldZ) {
        if (!Config.enableFogOfWar) return true;
        if (minecraft.player == null) return false;
        int tileSize = Config.tileSizeBlocks;
        int tx = Math.floorDiv(worldX, tileSize);
        int tz = Math.floorDiv(worldZ, tileSize);
        var dim = minecraft.player.level().dimension();
        return TileManager.get().isVisited(new TilePos(dim, tx, tz));
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
        requestedTiles.clear();
        frameCount = 0;
    }
}
