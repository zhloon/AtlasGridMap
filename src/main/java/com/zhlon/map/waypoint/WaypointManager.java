package com.zhlon.map.waypoint;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Waypoint 管理器。
 *
 * <p>管理所有 waypoint 的增删查。支持按维度、可见类型过滤。
 * 单例模式。
 */
public final class WaypointManager {

    private static final WaypointManager INSTANCE = new WaypointManager();

    private final Map<UUID, WaypointData> waypoints;

    private WaypointManager() {
        this.waypoints = new ConcurrentHashMap<>();
    }

    public static WaypointManager get() {
        return INSTANCE;
    }

    /** 添加 waypoint */
    public void add(WaypointData wp) {
        waypoints.put(wp.id(), wp);
    }

    /** 删除 waypoint */
    public void remove(UUID id) {
        waypoints.remove(id);
    }

    /** 更新 waypoint */
    public void update(WaypointData wp) {
        waypoints.put(wp.id(), wp);
    }

    /** 获取指定 waypoint */
    public Optional<WaypointData> get(UUID id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    /** 获取某玩家的所有 waypoint */
    public List<WaypointData> getByOwner(UUID owner) {
        return waypoints.values().stream()
                .filter(wp -> wp.owner().equals(owner))
                .toList();
    }

    /** 获取指定维度中当前玩家可见的 waypoint（按类型过滤） */
    public List<WaypointData> getVisibleFor(UUID viewer, ResourceKey<Level> dimension) {
        return waypoints.values().stream()
                .filter(wp -> wp.dimension().equals(dimension))
                .filter(wp -> isVisible(wp, viewer))
                .toList();
    }

    /** 获取指定维度的所有 waypoint */
    public List<WaypointData> getByDimension(ResourceKey<Level> dimension) {
        return waypoints.values().stream()
                .filter(wp -> wp.dimension().equals(dimension))
                .toList();
    }

    /** 获取所有 waypoint */
    public Collection<WaypointData> all() {
        return waypoints.values();
    }

    /** 获取总数 */
    public int count() {
        return waypoints.size();
    }

    /** 清除所有 */
    public void clear() {
        waypoints.clear();
    }

    private boolean isVisible(WaypointData wp, UUID viewer) {
        if (wp.owner().equals(viewer)) return true;
        return switch (wp.type()) {
            case PRIVATE -> wp.owner().equals(viewer);
            case GLOBAL -> true;
            default -> true;
        };
    }
}
