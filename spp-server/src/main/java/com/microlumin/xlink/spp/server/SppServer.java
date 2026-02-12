package com.microlumin.xlink.spp.server;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.microlumin.xlink.spp.common.SppCallback;
import com.microlumin.xlink.spp.common.SppConstants;
import com.microlumin.xlink.spp.common.SppSocketWrapper;

import java.io.IOException;

public class SppServer {
    private static final String TAG = "SppServer";
    private static final String NAME = "SppServer";

    private final BluetoothAdapter bluetoothAdapter;
    private AcceptThread acceptThread;
    private SppSocketWrapper socketWrapper;
    private final SppCallback callback;

    public SppServer(SppCallback callback) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
    }

    public synchronized void start() {
        Log.d(TAG, "start()");
        stop();
        acceptThread = new AcceptThread();
        acceptThread.start();
    }

    public synchronized void stop() {
        Log.d(TAG, "stop()");
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (socketWrapper != null) {
            socketWrapper.stop();
            socketWrapper = null;
        }
    }

    public synchronized boolean send(byte[] data) {
        if (socketWrapper != null) {
            return socketWrapper.send(data);
        }
        return false;
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, SppConstants.SPP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket listen() failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "AcceptThread started");
            BluetoothSocket socket = null;
            while (true) {
                try {
                    if (serverSocket == null) {
                        Log.e(TAG, "serverSocket is null, exiting");
                        break;
                    }
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.d(TAG, "Socket accept() failed or serverSocket closed: " + e.getMessage());
                    break;
                }

                if (socket != null) {
                    synchronized (SppServer.this) {
                        if (acceptThread != this) {
                            // 这个线程已经被取消了，关闭这个socket
                            Log.d(TAG, "AcceptThread has been cancelled, closing accepted socket");
                            try {
                                socket.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Error closing redundant socket", e);
                            }
                            break;
                        }
                        manageConnectedSocket(socket);
                        cancel(); // 连接成功后停止监听（单连接逻辑）
                        break;
                    }
                }
            }
            Log.d(TAG, "AcceptThread finished");
        }

        private void cancel() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "serverSocket close() failed", e);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void manageConnectedSocket(BluetoothSocket socket) {
        socketWrapper = new SppSocketWrapper(socket, callback);
        socketWrapper.start();
        if (callback != null) {
            callback.onConnected(socket.getRemoteDevice().getName(), socket.getRemoteDevice().getAddress());
        }
    }
}