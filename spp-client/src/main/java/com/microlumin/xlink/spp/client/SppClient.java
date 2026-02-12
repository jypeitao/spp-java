package com.microlumin.xlink.spp.client;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.microlumin.xlink.spp.common.SppCallback;
import com.microlumin.xlink.spp.common.SppConstants;
import com.microlumin.xlink.spp.common.SppSocketWrapper;

import java.io.IOException;

public class SppClient {
    private static final String TAG = "SppClient";

    private final BluetoothAdapter bluetoothAdapter;
    private ConnectThread connectThread;
    private SppSocketWrapper socketWrapper;
    private final SppCallback callback;

    public SppClient(SppCallback callback) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
    }

    public synchronized void connect(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        connect(device);
    }

    public synchronized void connect(BluetoothDevice device) {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    public synchronized void disconnect() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
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

    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;

        @SuppressLint("MissingPermission")
        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            socket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            if (bluetoothAdapter.isDiscovering()) {
                Log.i(TAG, "cancelDiscovery");
                bluetoothAdapter.cancelDiscovery();
            }

            try {
                if (socket == null) return;
                socket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Socket connect() failed", e);
                try {
                    socket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() socket during connection failure", e2);
                }
                synchronized (SppClient.this) {
                    if (connectThread == this) {
                        if (callback != null) callback.onError("Connection failed: " + e.getMessage());
                    }
                }
                return;
            }

            synchronized (SppClient.this) {
                if (connectThread != this) {
                    // This thread was cancelled or replaced
                    Log.d(TAG, "ConnectThread has been cancelled, closing socket");
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing redundant socket", e);
                    }
                    return;
                }
                connectThread = null;
                manageConnectedSocket(socket);
            }
        }

        public void cancel() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "socket close() failed", e);
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