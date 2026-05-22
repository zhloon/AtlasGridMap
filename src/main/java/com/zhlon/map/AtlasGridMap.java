package com.zhlon.map;

import com.mojang.logging.LogUtils;
import com.zhlon.map.cache.CacheManager;
import com.zhlon.map.network.NetworkHandler;
import com.zhlon.map.render.MinimapOverlay;
import com.zhlon.map.render.WorldMapScreen;
import com.zhlon.map.tile.PeerManager;
import com.zhlon.map.tile.TileManager;
import com.zhlon.map.tile.TilePos;
import com.zhlon.map.waypoint.WaypointManager;
import com.zhlon.map.waypoint.WaypointData;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Atlas Map - 去中心化 P2P 共享地图系统。
 *
 * <p>核心功能：
 * <ul>
 *   <li>Tile 流式加载的小地图与世界地图</li>
 *   <li>P2P 网络共享地图 Tile</li>
 *   <li>全服共享探索与战争迷雾</li>
 *   <li>Waypoint 系统</li>
 *   <li>异步地图生成与本地缓存</li>
 * </ul>
 */
@Mod(AtlasGridMap.MODID)
public class AtlasGridMap {

    /** Mod 标识符，与 mods.toml 和 gradle.properties 中的 mod_id 保持一致 */
    public static final String MODID = "atlasmap";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AtlasGridMap(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    /** 双端通用初始化。 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        TileManager.init(FMLPaths.GAMEDIR.get());
        NetworkHandler.register();
        LOGGER.info("Atlas Map common setup complete");
    }

    /** 服务端 Tick：定期清理过期缓存 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long tickCount = event.getServer().getTickCount();
        if (tickCount % 1200 == 0) {
            CacheManager cacheMgr = new CacheManager(FMLPaths.GAMEDIR.get().resolve("atlasmap"));
            long maxBytes = (long) Config.maxCacheSizeMb * 1024L * 1024L;
            cacheMgr.evictByMaxBytes(maxBytes);
        }
    }

    /** 服务端启动回调。 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        TileManager.get().setServerHost("local");
        LOGGER.info("Atlas Map server starting");
    }

    /** 服务端关闭回调。 */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TileManager.get().shutdown();
        LOGGER.info("Atlas Map server stopping");
    }

    /** 玩家离开时清理节点索引。 */
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        PeerManager.get().removePeer(event.getEntity().getUUID());
    }

    /** 区块加载。 */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof Level level) {
            int chunkX = event.getChunk().getPos().x;
            int chunkZ = event.getChunk().getPos().z;
            TileManager.get().markChunkDirty(level.dimension(), chunkX, chunkZ);
        }
    }

    /** 方块放置。 */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) {
            TilePos pos = TilePos.fromBlock(level.dimension(),
                    event.getPos().getX(), event.getPos().getZ());
            TileManager.get().markDirty(pos);
        }
    }

    /** 方块破坏。 */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level) {
            TilePos pos = TilePos.fromBlock(level.dimension(),
                    event.getPos().getX(), event.getPos().getZ());
            TileManager.get().markDirty(pos);
        }
    }

    /** 世界加载。 */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof Level level) {
            LOGGER.info("Atlas Map world loaded: {}", level.dimension().location());
        }
    }

    /** 仅在物理客户端执行的 Mod 初始化事件。 */
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        public static final KeyMapping OPEN_MAP_KEY = new KeyMapping(
                "key.atlasmap.open_map",
                GLFW.GLFW_KEY_M,
                "key.categories.atlasmap"
        );

        public static final MinimapOverlay MINIMAP = new MinimapOverlay();

        /** 注册按键绑定。 */
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MAP_KEY);
        }

        /** 客户端专属初始化。 */
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            MinecraftForge.EVENT_BUS.register(new ClientForgeEvents());
            LOGGER.info("Atlas Map client setup complete");
        }
    }

    /** 客户端 Forge 事件处理（需要实例注册，不能是静态内部类里直接注解） */
    public static class ClientForgeEvents {

        /** 渲染 HUD 小地图。 */
        @SubscribeEvent
        public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
            ClientModEvents.MINIMAP.render(event);
        }

        /** 客户端 Tick：检测按键打开世界地图。 */
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            while (ClientModEvents.OPEN_MAP_KEY.consumeClick()) {
                mc.setScreen(new WorldMapScreen());
            }
        }

        /** 按键输入事件（备用） */
        @SubscribeEvent
        public void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            if (ClientModEvents.OPEN_MAP_KEY.consumeClick()) {
                mc.setScreen(new WorldMapScreen());
            }
        }
    }
}
