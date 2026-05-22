package com.zhlon.map.network;

import com.zhlon.map.tile.TilePos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Tile 广播数据包。
 *
 * <p>当客户端生成新 Tile 后，向服务端广播其 Tile 位置和哈希。
 * 服务端收到后转发给所有其他客户端，用于更新节点索引。
 */
public class TileBroadcastPacket {

    private final ResourceLocation dimension;
    private final int x;
    private final int z;
    private final int zoom;
    private final byte[] hash;

    public TileBroadcastPacket(TilePos pos, byte[] hash) {
        this.dimension = pos.dimension().location();
        this.x = pos.x();
        this.z = pos.z();
        this.zoom = pos.zoom();
        this.hash = hash;
    }

    public TileBroadcastPacket(ResourceLocation dimension, int x, int z, int zoom, byte[] hash) {
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.zoom = zoom;
        this.hash = hash;
    }

    public static void encode(TileBroadcastPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension);
        buf.writeInt(packet.x);
        buf.writeInt(packet.z);
        buf.writeInt(packet.zoom);
        buf.writeByteArray(packet.hash);
    }

    public static TileBroadcastPacket decode(FriendlyByteBuf buf) {
        return new TileBroadcastPacket(
                buf.readResourceLocation(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readByteArray()
        );
    }

    /** 服务端处理：转发给所有其他客户端 */
    public static void handle(TileBroadcastPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;

            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, packet.dimension);
            TilePos pos = new TilePos(dimKey, packet.x, packet.z, packet.zoom);

            com.zhlon.map.tile.PeerManager.get().updatePeer(sender, pos);

            NetworkHandler.sendToAllExcept(
                    new TileBroadcastPacket(pos, packet.hash), sender);
        });
        ctx.get().setPacketHandled(true);
    }

    public TilePos toTilePos() {
        ResourceKey<Level> dimKey = ResourceKey.create(
                Registries.DIMENSION, dimension);
        return new TilePos(dimKey, x, z, zoom);
    }

    @Override
    public String toString() {
        return "TileBroadcast[" + dimension + " " + x + "_" + z
                + " hash=" + HexFormat.of().formatHex(hash, 0, 4) + "...]";
    }
}
