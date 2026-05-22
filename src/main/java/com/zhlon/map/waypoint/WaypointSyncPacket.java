package com.zhlon.map.waypoint;

import com.zhlon.map.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Waypoint 同步数据包。
 *
 * <p>客户端与服务端之间同步 waypoint 的增删操作。
 */
public class WaypointSyncPacket {

    private final Action action;
    private final UUID waypointId;
    private final UUID ownerId;
    private final String name;
    private final ResourceLocation dimension;
    private final BlockPos pos;
    private final int color;
    private final String typeName;

    private WaypointSyncPacket(Action action, UUID waypointId, UUID ownerId, String name,
                                ResourceLocation dimension, BlockPos pos, int color, String typeName) {
        this.action = action;
        this.waypointId = waypointId;
        this.ownerId = ownerId;
        this.name = name;
        this.dimension = dimension;
        this.pos = pos;
        this.color = color;
        this.typeName = typeName;
    }

    public static WaypointSyncPacket add(WaypointData wp) {
        return new WaypointSyncPacket(Action.ADD, wp.id(), wp.owner(), wp.name(),
                wp.dimension().location(), wp.pos(), wp.color(), wp.type().name());
    }

    public static WaypointSyncPacket remove(UUID waypointId) {
        return new WaypointSyncPacket(Action.REMOVE, waypointId, null, "",
                null, null, 0, "");
    }

    public static void encode(WaypointSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.action);
        buf.writeUUID(packet.waypointId);
        if (packet.action == Action.ADD) {
            buf.writeUUID(packet.ownerId);
            buf.writeUtf(packet.name);
            buf.writeResourceLocation(packet.dimension);
            buf.writeBlockPos(packet.pos);
            buf.writeInt(packet.color);
            buf.writeUtf(packet.typeName);
        }
    }

    public static WaypointSyncPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID waypointId = buf.readUUID();
        if (action == Action.ADD) {
            UUID ownerId = buf.readUUID();
            String name = buf.readUtf();
            ResourceLocation dim = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            int color = buf.readInt();
            String typeName = buf.readUtf();
            return new WaypointSyncPacket(action, waypointId, ownerId, name, dim, pos, color, typeName);
        }
        return new WaypointSyncPacket(action, waypointId, null, "", null, null, 0, "");
    }

    public static void handle(WaypointSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (packet.action == Action.ADD) {
                ResourceKey<Level> dimKey = ResourceKey.create(
                        Registries.DIMENSION, packet.dimension);
                WaypointData.WaypointType type = WaypointData.WaypointType.valueOf(packet.typeName);

                var existing = WaypointManager.get().get(packet.waypointId);
                if (existing.isPresent() && existing.get().owner().equals(packet.ownerId)) {
                    WaypointManager.get().update(new WaypointData(packet.waypointId,
                            packet.ownerId, packet.name, dimKey, packet.pos, packet.color, type));
                } else {
                    WaypointManager.get().add(new WaypointData(packet.waypointId,
                            packet.ownerId, packet.name, dimKey, packet.pos, packet.color, type));
                }

                var sender = ctx.get().getSender();
                if (sender != null) {
                    NetworkHandler.sendToAllExcept(packet, sender);
                }
            } else {
                WaypointManager.get().remove(packet.waypointId);
                var sender = ctx.get().getSender();
                if (sender != null) {
                    NetworkHandler.sendToAllExcept(packet, sender);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public enum Action {
        ADD,
        REMOVE
    }
}
