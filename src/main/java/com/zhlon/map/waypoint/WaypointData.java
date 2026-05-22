package com.zhlon.map.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Waypoint 数据。
 *
 * <p>支持四种可见范围：私人、队伍、公会、全服。
 */
public class WaypointData {

    private final UUID id;
    private final UUID owner;
    private final String name;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final int color;
    private final WaypointType type;

    public WaypointData(UUID id, UUID owner, String name, ResourceKey<Level> dimension,
                        BlockPos pos, int color, WaypointType type) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.dimension = dimension;
        this.pos = pos;
        this.color = color;
        this.type = type;
    }

    public static WaypointData create(UUID owner, String name, ResourceKey<Level> dimension,
                                       BlockPos pos, int color, WaypointType type) {
        return new WaypointData(UUID.randomUUID(), owner, name, dimension, pos, color, type);
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String name() { return name; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos pos() { return pos; }
    public int color() { return color; }
    public WaypointType type() { return type; }

    @Override
    public String toString() {
        return "Waypoint[" + name + " " + pos + "]";
    }

    public enum WaypointType {
        PRIVATE,
        TEAM,
        GUILD,
        GLOBAL
    }
}
