package com.zhlon.map.network;

import com.zhlon.map.AtlasGridMap;
import com.zhlon.map.waypoint.WaypointSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Atlas Map 网络通信注册中心。
 *
 * <p>使用 Forge SimpleChannel 在客户端与服务端之间传递 Tile 请求/响应/广播。
 * P2P 语义由服务端中转实现：客户端 A → 服务端 → 客户端 B。
 */
public final class NetworkHandler {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AtlasGridMap.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int packetId;

    private NetworkHandler() {}

    /** 注册所有网络消息类型 */
    public static void register() {
        CHANNEL.registerMessage(packetId++,
                TileRequestPacket.class,
                TileRequestPacket::encode,
                TileRequestPacket::decode,
                TileRequestPacket::handle);

        CHANNEL.registerMessage(packetId++,
                TileResponsePacket.class,
                TileResponsePacket::encode,
                TileResponsePacket::decode,
                TileResponsePacket::handle);

        CHANNEL.registerMessage(packetId++,
                TileBroadcastPacket.class,
                TileBroadcastPacket::encode,
                TileBroadcastPacket::decode,
                TileBroadcastPacket::handle);

        CHANNEL.registerMessage(packetId++,
                WaypointSyncPacket.class,
                WaypointSyncPacket::encode,
                WaypointSyncPacket::decode,
                WaypointSyncPacket::handle);
    }

    /** 发送数据包到服务端 */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /** 发送数据包到指定客户端 */
    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 广播数据包到所有客户端 */
    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /** 广播数据包给除指定玩家外的所有客户端 */
    public static void sendToAllExcept(Object packet, ServerPlayer exclude) {
        CHANNEL.send(PacketDistributor.NMLIST.with(() -> java.util.Collections.singletonList(exclude.connection.connection)), packet);
    }
}
