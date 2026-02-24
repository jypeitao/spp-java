package com.microlumin.xlink.spp.common;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * SPP 数据包解析器，实现基于 [包头 + 长度 + 数据] 的组包逻辑。
 */
public class SppPacketDecoder {
    private static final String TAG = "SppPacketDecoder";
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * 将新收到的字节流存入缓冲区并解析完整包。
     *
     * @param data 新收到的字节数据
     * @return 解析出的完整包列表（payload 部分）
     */
    public synchronized List<byte[]> decode(byte[] data) {
        List<byte[]> packets = new ArrayList<>();
        buffer.write(data, 0, data.length);

        while (true) {
            byte[] currentBytes = buffer.toByteArray();
            int headerPos = findHeader(currentBytes);

            if (headerPos == -1) {
                // 未找到包头，如果缓冲区过大且无包头，清理一部分防止内存泄漏
                if (currentBytes.length > 4096) {
                    buffer.reset();
                }
                break;
            }

            // 找到包头，检查长度字段是否完整
            int minPacketSize = SppConstants.PACKET_HEADER.length + SppConstants.PACKET_LENGTH_SIZE;
            if (currentBytes.length < headerPos + minPacketSize) {
                // 长度字段还没收全，等待后续数据
                break;
            }

            // 读取数据长度
            ByteBuffer bb = ByteBuffer.wrap(currentBytes, headerPos + SppConstants.PACKET_HEADER.length, SppConstants.PACKET_LENGTH_SIZE);
            int payloadLength = bb.getInt();

            if (payloadLength < 0 || payloadLength > 10 * 1024 * 1024) { // 限制最大 10MB，防止恶意长度导致 OOM
                XLog.e(TAG, "Invalid payload length: " + payloadLength + ", dropping data at " + headerPos);
                // 丢弃当前包头，继续从下一个字节找包头
                byte[] remaining = new byte[currentBytes.length - (headerPos + 1)];
                System.arraycopy(currentBytes, headerPos + 1, remaining, 0, remaining.length);
                buffer.reset();
                buffer.write(remaining, 0, remaining.length);
                continue;
            }

            // 检查整个包是否收全
            int totalPacketSize = minPacketSize + payloadLength;
            if (currentBytes.length < headerPos + totalPacketSize) {
                // 包还没收全，等待后续数据
                break;
            }

            // 提取 payload
            byte[] payload = new byte[payloadLength];
            System.arraycopy(currentBytes, headerPos + minPacketSize, payload, 0, payloadLength);
            packets.add(payload);

            // 从缓冲区移除已处理的数据（包括包头之前的垃圾数据和当前完整的包）
            int nextStart = headerPos + totalPacketSize;
            int remainingLen = currentBytes.length - nextStart;
            buffer.reset();
            if (remainingLen > 0) {
                buffer.write(currentBytes, nextStart, remainingLen);
            } else {
                break; // 缓冲区已空
            }
        }

        return packets;
    }

    private int findHeader(byte[] data) {
        byte[] header = SppConstants.PACKET_HEADER;
        if (data.length < header.length) return -1;

        for (int i = 0; i <= data.length - header.length; i++) {
            boolean match = true;
            for (int j = 0; j < header.length; j++) {
                if (data[i + j] != header[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    /**
     * 将 payload 封装成协议包（包头 + 长度 + payload）。
     */
    public static byte[] encode(byte[] payload) {
        ByteBuffer bb = ByteBuffer.allocate(SppConstants.PACKET_HEADER.length + SppConstants.PACKET_LENGTH_SIZE + payload.length);
        bb.put(SppConstants.PACKET_HEADER);
        bb.putInt(payload.length);
        bb.put(payload);
        return bb.array();
    }

    public synchronized void reset() {
        buffer.reset();
    }
}