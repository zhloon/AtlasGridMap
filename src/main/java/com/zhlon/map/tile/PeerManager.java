package com.zhlon.map.tile;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点索引管理器。
 *
 * <p>维护"Tile → 拥有该 Tile 的玩家列表"映射，用于 P2P 下载路由。
 * 单例模式，由 NetworkHandler 的事件处理更新。
 */
public final class PeerManager {

    private static final PeerManager INSTANCE = new PeerManager();

    private final Map<TilePos, Set<UUID>> tilePeers;
    private final Map<UUID, Set<TilePos>> peerTiles;

    private PeerManager() {
        this.tilePeers = new ConcurrentHashMap<>();
        this.peerTiles = new ConcurrentHashMap<>();
    }

    public static PeerManager get() {
        return INSTANCE;
    }

    /** 更新节点拥有的 Tile 记录（根据广播消息调用） */
    public void updatePeer(ServerPlayer player, TilePos pos) {
        UUID id = player.getUUID();
        tilePeers.computeIfAbsent(pos, k -> ConcurrentHashMap.newKeySet()).add(id);
        peerTiles.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    /** 查找拥有指定 Tile 的所有节点 UUID */
    public Set<UUID> getPeersFor(TilePos pos) {
        return tilePeers.getOrDefault(pos, Set.of());
    }

    /** 查找某节点拥有的所有 Tile */
    public Set<TilePos> getTilesFor(UUID peer) {
        return peerTiles.getOrDefault(peer, Set.of());
    }

    /** 获取所有已知节点 */
    public Set<UUID> allPeers() {
        return peerTiles.keySet();
    }

    /** 获取所有已知 Tile */
    public Set<TilePos> allKnownTiles() {
        return tilePeers.keySet();
    }

    /** 节点离开时清理 */
    public void removePeer(UUID peerId) {
        Set<TilePos> tiles = peerTiles.remove(peerId);
        if (tiles != null) {
            for (TilePos pos : tiles) {
                Set<UUID> peers = tilePeers.get(pos);
                if (peers != null) {
                    peers.remove(peerId);
                    if (peers.isEmpty()) {
                        tilePeers.remove(pos);
                    }
                }
            }
        }
    }

    /** 获取已知节点数量 */
    public int peerCount() {
        return peerTiles.size();
    }

    /** 获知已知 Tile 数量 */
    public int knownTileCount() {
        return tilePeers.size();
    }
}
