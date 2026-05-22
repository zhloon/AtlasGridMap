package com.zhlon.map;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Atlas Map 配置文件。
 *
 * <p>分为两部分：
 * <ul>
 *   <li><b>服务端配置</b>（{@code atlasmap-common.toml}）—— Tile、P2P、探索等双端共用配置</li>
 *   <li><b>客户端配置</b>（{@code atlasmap-client.toml}）—— 小地图 HUD、渲染等纯客户端配置</li>
 * </ul>
 *
 * <p>所有配置通过 Forge 的 {@code .toml} 文件持久化，修改后热加载生效。
 */
@Mod.EventBusSubscriber(modid = AtlasGridMap.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    // ==================== 服务端 / 双端共用配置 ====================

    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

    /** Tile 尺寸（blocks），必须是 2 的幂。默认 512（即 32×32 chunks） */
    public static final ForgeConfigSpec.IntValue TILE_SIZE_BLOCKS = COMMON_BUILDER
            .comment("Tile size in blocks (must be power of 2)")
            .defineInRange("tileSizeBlocks", 512, 128, 2048);

    /** Tile 纹理分辨率（像素），默认 128 */
    public static final ForgeConfigSpec.IntValue TILE_TEXTURE_RESOLUTION = COMMON_BUILDER
            .comment("Tile texture resolution in pixels")
            .defineInRange("tileTextureResolution", 128, 64, 512);

    /** 本地 Tile 缓存上限（MB），超出后触发 LRU 淘汰。默认 2 GB */
    public static final ForgeConfigSpec.IntValue MAX_CACHE_SIZE_MB = COMMON_BUILDER
            .comment("Maximum local tile cache size in megabytes")
            .defineInRange("maxCacheSizeMb", 2048, 256, 8192);

    /** GPU 纹理 LRU 数量上限，防止显存溢出。默认 256 */
    public static final ForgeConfigSpec.IntValue MAX_GPU_TEXTURES = COMMON_BUILDER
            .comment("Maximum GPU texture count (LRU eviction)")
            .defineInRange("maxGpuTextures", 256, 64, 1024);

    /** ZSTD 压缩级别（1-22），默认 3 */
    public static final ForgeConfigSpec.IntValue TILE_COMPRESSION_LEVEL = COMMON_BUILDER
            .comment("ZSTD compression level for tiles (1-22)")
            .defineInRange("tileCompressionLevel", 3, 1, 22);

    /** 是否启用 P2P Tile 同步。默认开启 */
    public static final ForgeConfigSpec.BooleanValue ENABLE_P2P_SYNC = COMMON_BUILDER
            .comment("Enable P2P tile synchronization")
            .define("enableP2PSync", true);

    /** P2P 网络监听端口。默认 28560 */
    public static final ForgeConfigSpec.IntValue P2P_PORT = COMMON_BUILDER
            .comment("P2P network port")
            .defineInRange("p2pPort", 28560, 1024, 65535);

    /** 最大 P2P 节点连接数。默认 8 */
    public static final ForgeConfigSpec.IntValue MAX_PEER_CONNECTIONS = COMMON_BUILDER
            .comment("Maximum P2P peer connections")
            .defineInRange("maxPeerConnections", 8, 2, 32);

    /** 全服共享探索：任意玩家探索 → 全服同步探索区域。默认开启 */
    public static final ForgeConfigSpec.BooleanValue SHARED_EXPLORATION = COMMON_BUILDER
            .comment("Enable shared exploration (all players share explored areas)")
            .define("sharedExploration", true);

    /** 战争迷雾：未探索区域以黑色遮罩覆盖。默认开启 */
    public static final ForgeConfigSpec.BooleanValue ENABLE_FOG_OF_WAR = COMMON_BUILDER
            .comment("Enable fog of war (unexplored areas appear dark)")
            .define("enableFogOfWar", true);

    /** 构建并导出服务端配置规格 */
    static final ForgeConfigSpec SPEC = COMMON_BUILDER.build();

    // ==================== 客户端专属配置 ====================

    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();

    /** 是否显示 HUD 小地图。默认开启 */
    public static final ForgeConfigSpec.BooleanValue ENABLE_MINIMAP = CLIENT_BUILDER
            .comment("Enable the HUD minimap")
            .define("enableMinimap", true);

    /** 小地图在屏幕上的位置。默认右上角 */
    public static final ForgeConfigSpec.EnumValue<MinimapPosition> MINIMAP_POSITION = CLIENT_BUILDER
            .comment("Minimap position on screen")
            .defineEnum("minimapPosition", MinimapPosition.TOP_RIGHT);

    /** 小地图显示尺寸（像素）。默认 160 */
    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE = CLIENT_BUILDER
            .comment("Minimap display size in pixels")
            .defineInRange("minimapSize", 160, 80, 320);

    /** 小地图缩放级别。默认 1（1:1） */
    public static final ForgeConfigSpec.IntValue MINIMAP_ZOOM = CLIENT_BUILDER
            .comment("Minimap zoom level")
            .defineInRange("minimapZoom", 1, 1, 8);

    /** 小地图是否随玩家朝向旋转。默认开启 */
    public static final ForgeConfigSpec.BooleanValue MINIMAP_ROTATION = CLIENT_BUILDER
            .comment("Enable minimap rotation with player direction")
            .define("minimapRotation", true);

    /** 小地图是否显示 waypoint 标记。默认开启 */
    public static final ForgeConfigSpec.BooleanValue SHOW_WAYPOINTS_MINIMAP = CLIENT_BUILDER
            .comment("Show waypoints on minimap")
            .define("showWaypointsMinimap", true);

    /** 小地图是否显示其他玩家位置。默认开启 */
    public static final ForgeConfigSpec.BooleanValue SHOW_PLAYERS_MINIMAP = CLIENT_BUILDER
            .comment("Show other players on minimap")
            .define("showPlayersMinimap", true);

    /**
     * 小地图是否显示敌对生物。默认关闭（避免过于杂乱 / 性能考虑）。
     */
    public static final ForgeConfigSpec.BooleanValue SHOW_ENTITIES_MINIMAP = CLIENT_BUILDER
            .comment("Show hostile entities on minimap")
            .define("showEntitiesMinimap", false);

    /** 构建并导出客户端配置规格 */
    static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    // ==================== 运行时字段（由 onLoad 填充） ====================

    public static int tileSizeBlocks;
    public static int tileTextureResolution;
    public static int maxCacheSizeMb;
    public static int maxGpuTextures;
    public static int tileCompressionLevel;
    public static boolean enableP2PSync;
    public static int p2pPort;
    public static int maxPeerConnections;
    public static boolean sharedExploration;
    public static boolean enableFogOfWar;

    public static boolean enableMinimap;
    public static MinimapPosition minimapPosition;
    public static int minimapSize;
    public static int minimapZoom;
    public static boolean minimapRotation;
    public static boolean showWaypointsMinimap;
    public static boolean showPlayersMinimap;
    public static boolean showEntitiesMinimap;

    // ==================== 枚举类型 ====================

    /** 小地图屏幕位置 */
    public enum MinimapPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    // ==================== 配置加载 ====================

    /** 服务端配置加载 / 热更新回调 */
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            tileSizeBlocks = TILE_SIZE_BLOCKS.get();
            tileTextureResolution = TILE_TEXTURE_RESOLUTION.get();
            maxCacheSizeMb = MAX_CACHE_SIZE_MB.get();
            maxGpuTextures = MAX_GPU_TEXTURES.get();
            tileCompressionLevel = TILE_COMPRESSION_LEVEL.get();
            enableP2PSync = ENABLE_P2P_SYNC.get();
            p2pPort = P2P_PORT.get();
            maxPeerConnections = MAX_PEER_CONNECTIONS.get();
            sharedExploration = SHARED_EXPLORATION.get();
            enableFogOfWar = ENABLE_FOG_OF_WAR.get();
        }
    }

    /** 客户端配置加载 / 热更新回调 */
    @SubscribeEvent
    static void onClientLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            enableMinimap = ENABLE_MINIMAP.get();
            minimapPosition = MINIMAP_POSITION.get();
            minimapSize = MINIMAP_SIZE.get();
            minimapZoom = MINIMAP_ZOOM.get();
            minimapRotation = MINIMAP_ROTATION.get();
            showWaypointsMinimap = SHOW_WAYPOINTS_MINIMAP.get();
            showPlayersMinimap = SHOW_PLAYERS_MINIMAP.get();
            showEntitiesMinimap = SHOW_ENTITIES_MINIMAP.get();
        }
    }
}
