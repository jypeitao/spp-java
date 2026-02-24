package com.microlumin.xlink.spp.common;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class SppSocketWrapper {
    private static final String TAG = "SppSocketWrapper";
    private final BluetoothSocket socket;
    private final SppCallback callback;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isRunning = false;
    private Thread readThread;
    private final SppPacketDecoder decoder = new SppPacketDecoder();

    public SppSocketWrapper(BluetoothSocket socket, SppCallback callback) {
        this.socket = socket;
        this.callback = callback;
        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (IOException e) {
            XLog.e(TAG, "Error getting streams", e);
        }
    }

    public void start() {
        if (inputStream == null || outputStream == null) {
            if (callback != null) callback.onError("Streams are not initialized");
            return;
        }
        isRunning = true;
        readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[1024];
                int bytes;
                while (isRunning) {
                    try {
                        bytes = inputStream.read(buffer);
                        if (bytes > 0) {
                            byte[] data = Arrays.copyOf(buffer, bytes);
                            if (callback != null) {
                                // 默认依然回调原始数据
                                callback.onDataReceived(data);

                                // 同时尝试解析协议包
                                List<byte[]> packets = decoder.decode(data);
                                for (byte[] payload : packets) {
                                    callback.onPacketReceived(payload);
                                }
                            }
                        } else if (bytes == -1) {
                            XLog.d(TAG, "Socket closed by remote");
                            break;
                        }
                    } catch (IOException e) {
                        if (isRunning) {
                            XLog.e(TAG, "Error reading from stream", e);
                            if (callback != null) callback.onStateChanged(SppState.DISCONNECTED, null, null);
                        }
                        break;
                    }
                }
                stop();
            }
        });
        readThread.start();
    }

    public synchronized boolean send(byte[] data) {
        return sendInternal(data);
    }

    /**
     * 发送协议包（自动添加包头和长度）。
     */
    public synchronized boolean sendPacket(byte[] payload) {
        byte[] packet = SppPacketDecoder.encode(payload);
        return sendInternal(packet);
    }

    private boolean sendInternal(byte[] data) {
        if (outputStream != null) {
            try {
                int offset = 0;
                while (offset < data.length) {
                    int length = Math.min(data.length - offset, SppConstants.MAX_WRITE_SIZE);
                    outputStream.write(data, offset, length);
                    offset += length;
                }
                outputStream.flush();
                return true;
            } catch (IOException e) {
                XLog.e(TAG, "Error writing to stream", e);
                if (callback != null) callback.onError("Send failed: " + e.getMessage());
            }
        }
        return false;
    }

    public void stop() {
        isRunning = false;
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            XLog.e(TAG, "Error closing socket", e);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }
}