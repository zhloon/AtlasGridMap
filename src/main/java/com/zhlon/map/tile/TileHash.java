package com.zhlon.map.tile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Tile 数据哈希工具。
 *
 * <p>使用 SHA-256 作为默认哈希算法，用于 Tile 唯一标识、完整性校验和去重。
 * 后续可切换为 Blake3。
 */
public final class TileHash {

    private static final String ALGORITHM = "SHA-256";

    private TileHash() {}

    /** 计算字节数组的哈希 */
    public static byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance(ALGORITHM).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** 计算字节数组的哈希，返回十六进制字符串 */
    public static String hashHex(byte[] data) {
        return HexFormat.of().formatHex(hash(data));
    }

    /** 校验数据是否与给定哈希匹配 */
    public static boolean verify(byte[] data, byte[] expectedHash) {
        return MessageDigest.isEqual(hash(data), expectedHash);
    }

    /** 哈希字节长度（SHA-256 = 32） */
    public static int hashLength() {
        return 32;
    }
}
