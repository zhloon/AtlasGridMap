package com.zhlon.map.tile;

/**
 * Tile 数据容器。
 *
 * <p>包含 Tile 的纹理像素、探索遮罩和完整性哈希。
 * 纹理和遮罩均为压缩存储，使用时需解压。
 */
public class TileData {

    private final TilePos pos;
    private final int version;
    private final long updateTime;
    private final byte[] compressedTexture;
    private final byte[] exploredMask;
    private final byte[] hash;

    public TileData(TilePos pos, int version, long updateTime,
                    byte[] compressedTexture, byte[] exploredMask) {
        this.pos = pos;
        this.version = version;
        this.updateTime = updateTime;
        this.compressedTexture = compressedTexture;
        this.exploredMask = exploredMask;
        this.hash = computeHash();
    }

    public TileData(TilePos pos, int version, long updateTime,
                    byte[] compressedTexture, byte[] exploredMask, byte[] hash) {
        this.pos = pos;
        this.version = version;
        this.updateTime = updateTime;
        this.compressedTexture = compressedTexture;
        this.exploredMask = exploredMask;
        this.hash = hash;
    }

    private byte[] computeHash() {
        int totalLen = compressedTexture.length + exploredMask.length;
        byte[] combined = new byte[totalLen];
        System.arraycopy(compressedTexture, 0, combined, 0, compressedTexture.length);
        System.arraycopy(exploredMask, 0, combined, compressedTexture.length, exploredMask.length);
        return TileHash.hash(combined);
    }

    /** 验证数据完整性 */
    public boolean verifyIntegrity() {
        byte[] computed = computeHash();
        return java.security.MessageDigest.isEqual(computed, hash);
    }

    public TilePos pos() { return pos; }
    public int version() { return version; }
    public long updateTime() { return updateTime; }
    public byte[] compressedTexture() { return compressedTexture; }
    public byte[] exploredMask() { return exploredMask; }
    public byte[] hash() { return hash; }

    /** 全局唯一 Tile 标识：坐标 + 哈希 */
    public String tileId() {
        return pos.toString() + ":" + TileHash.hashHex(hash);
    }

    @Override
    public String toString() {
        return "TileData[pos=" + pos + ", version=" + version
                + ", textureSize=" + compressedTexture.length + "]";
    }
}
