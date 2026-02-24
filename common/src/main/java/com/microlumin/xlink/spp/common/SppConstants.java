package com.microlumin.xlink.spp.common;

import java.util.UUID;

public class SppConstants {
    public static final UUID STANDARD_SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final UUID SPP_UUID = UUID.fromString("A1B2C3D4-0000-1000-8000-00805F9B34FB");

    /**
     * 推荐的单次发送最大数据长度（字节）。
     * 虽然 RFCOMM 会处理分包，但为了兼容性和性能，建议单次 write 不要超过 1KB。
     */
    public static final int MAX_WRITE_SIZE = 1024;

    /**
     * 协议包头：0xAA 0x55
     */
    public static final byte[] PACKET_HEADER = {(byte) 0xAA, (byte) 0x55};

    /**
     * 协议长度字段大小（字节数）：int 类型为 4 字节
     */
    public static final int PACKET_LENGTH_SIZE = 4;
}