package com.microlumin.xlink.spp.common;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class SppSocketWrapper {
    private static final String TAG = "SppSocketWrapper";
    private final BluetoothSocket socket;
    private final SppCallback callback;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isRunning = false;
    private Thread readThread;

    public SppSocketWrapper(BluetoothSocket socket, SppCallback callback) {
        this.socket = socket;
        this.callback = callback;
        try {
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error getting streams", e);
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
                            if (callback != null) {
                                callback.onDataReceived(Arrays.copyOf(buffer, bytes));
                            }
                        } else if (bytes == -1) {
                            Log.d(TAG, "Socket closed by remote");
                            break;
                        }
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error reading from stream", e);
                            if (callback != null) callback.onDisconnected();
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
        if (outputStream != null) {
            try {
                outputStream.write(data);
                outputStream.flush();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Error writing to stream", e);
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
            Log.e(TAG, "Error closing socket", e);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }
}