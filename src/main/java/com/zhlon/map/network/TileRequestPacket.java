package com.zhlon.map.network;

import com.zhlon.map.tile.TileHash;
import com.zhlon.map.tile.TilePos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tile 请求数据包。
 * 客户端向服务端请求指定 Tile 的数据。
 * 服务端查找本地缓存或转发给拥有该 Tile 的其他节点。
 */
public class TileRequestPacket {

    private final ResourceLocation dimension;
    private final int x;
    private final int z;
    private final int zoom;

    public TileRequestPacket(TilePos pos) {
        this.dimension = pos.dimension().location();
        this.x = pos.x();
        this.z = pos.z();
        this.zoom = pos.zoom();
    }

    public TileRequestPacket(ResourceLocation dimension, int x, int z, int zoom) {
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.zoom = zoom;
    }

    public static void encode(TileRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension);
        buf.writeInt(packet.x);
        buf.writeInt(packet.z);
        buf.writeInt(packet.zoom);
    }

    public static TileRequestPacket decode(FriendlyByteBuf buf) {
        return new TileRequestPacket(
                buf.readResourceLocation(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(TileRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;

            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, packet.dimension);
            TilePos pos = new TilePos(dimKey, packet.x, packet.z, packet.zoom);

            var tileOpt = com.zhlon.map.tile.TileManager.get().getCached(pos);
            if (tileOpt.isPresent()) {
                var tile = tileOpt.get();
                NetworkHandler.sendToPlayer(
                        new TileResponsePacket(tile), sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public TilePos toTilePos() {
        ResourceKey<Level> dimKey = ResourceKey.create(
                Registries.DIMENSION, dimension);
        return new TilePos(dimKey, x, z, zoom);
    }
}
