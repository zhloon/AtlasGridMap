package com.zhlon.map.network;

import com.zhlon.map.tile.TileData;
import com.zhlon.map.tile.TilePos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tile 响应数据包。
 * 服务端向客户端发送请求的 Tile 完整数据。
 * 客户端接收后校验哈希并写入本地缓存。
 */
public class TileResponsePacket {

    private final ResourceLocation dimension;
    private final int x;
    private final int z;
    private final int zoom;
    private final int version;
    private final long updateTime;
    private final byte[] compressedTexture;
    private final byte[] exploredMask;
    private final byte[] hash;

    public TileResponsePacket(TileData tile) {
        this.dimension = tile.pos().dimension().location();
        this.x = tile.pos().x();
        this.z = tile.pos().z();
        this.zoom = tile.pos().zoom();
        this.version = tile.version();
        this.updateTime = tile.updateTime();
        this.compressedTexture = tile.compressedTexture();
        this.exploredMask = tile.exploredMask();
        this.hash = tile.hash();
    }

    public TileResponsePacket(ResourceLocation dimension, int x, int z, int zoom,
                               int version, long updateTime,
                               byte[] compressedTexture, byte[] exploredMask,
                               byte[] hash) {
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.zoom = zoom;
        this.version = version;
        this.updateTime = updateTime;
        this.compressedTexture = compressedTexture;
        this.exploredMask = exploredMask;
        this.hash = hash;
    }

    public static void encode(TileResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.dimension);
        buf.writeInt(packet.x);
        buf.writeInt(packet.z);
        buf.writeInt(packet.zoom);
        buf.writeInt(packet.version);
        buf.writeLong(packet.updateTime);
        buf.writeByteArray(packet.compressedTexture);
        buf.writeByteArray(packet.exploredMask);
        buf.writeByteArray(packet.hash);
    }

    public static TileResponsePacket decode(FriendlyByteBuf buf) {
        return new TileResponsePacket(
                buf.readResourceLocation(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readLong(),
                buf.readByteArray(),
                buf.readByteArray(),
                buf.readByteArray()
        );
    }

    public static void handle(TileResponsePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ResourceKey<Level> dimKey = ResourceKey.create(
                    Registries.DIMENSION, packet.dimension);
            TilePos pos = new TilePos(dimKey, packet.x, packet.z, packet.zoom);
            TileData tile = new TileData(pos, packet.version, packet.updateTime,
                    packet.compressedTexture, packet.exploredMask, packet.hash);

            com.zhlon.map.tile.TileManager.get().receiveTile(tile);
        });
        ctx.get().setPacketHandled(true);
    }
}
