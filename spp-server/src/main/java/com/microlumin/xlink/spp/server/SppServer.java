package com.microlumin.xlink.spp.server;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import com.microlumin.xlink.spp.common.XLog;
import com.microlumin.xlink.spp.common.SppCallback;
import com.microlumin.xlink.spp.common.SppConstants;
import com.microlumin.xlink.spp.common.SppSocketWrapper;

import java.io.IOException;

public class SppServer {
    private static final String TAG = "SppServer";
    private static final String NAME = "SppServer";
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private AcceptThread acceptThread;
    private SppSocketWrapper socketWrapper;
    private final SppCallback callback;
    private boolean isStarted = false;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                XLog.d(TAG, "Bluetooth state changed: " + state);
                synchronized (SppServer.this) {
                    if (!isStarted) return;
                    if (state == BluetoothAdapter.STATE_ON) {
                        XLog.d(TAG, "Bluetooth ON, starting AcceptThread");
                        startAcceptThread();
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        XLog.d(TAG, "Bluetooth OFF, stopping threads");
                        stopThreads();
                    }
                }
            }
        }
    };

    private final SppCallback internalCallback = new SppCallback() {
        @Override
        public void onConnected(String deviceName, String deviceAddress) {
            if (callback != null) callback.onConnected(deviceName, deviceAddress);
        }

        @Override
        public void onDisconnected() {
            if (callback != null) callback.onDisconnected();
            synchronized (SppServer.this) {
                if (isStarted && bluetoothAdapter.isEnabled()) {
                    XLog.d(TAG, "Re-starting AcceptThread after disconnection");
                    startAcceptThread();
                }
            }
        }

        @Override
        public void onDataReceived(byte[] data) {
            if (callback != null) callback.onDataReceived(data);
        }

        @Override
        public void onError(String message) {
            if (callback != null) callback.onError(message);
        }
    };

    public SppServer(Context context, SppCallback callback) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
    }

    public synchronized void start() {
        XLog.d(TAG, "start()");
        if (isStarted) return;
        isStarted = true;

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(bluetoothStateReceiver, filter);

        if (bluetoothAdapter.isEnabled()) {
            startAcceptThread();
        } else {
            XLog.w(TAG, "Bluetooth is disabled, waiting for it to turn on");
        }
    }

    public synchronized void stop() {
        XLog.d(TAG, "stop()");
        if (!isStarted) return;
        isStarted = false;

        try {
            context.unregisterReceiver(bluetoothStateReceiver);
        } catch (Exception e) {
            XLog.e(TAG, "Error unregistering receiver", e);
        }

        stopThreads();
    }

    private void stopThreads() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (socketWrapper != null) {
            socketWrapper.stop();
            socketWrapper = null;
        }
    }

    private void startAcceptThread() {
        if (acceptThread != null) {
            acceptThread.cancel();
        }
        acceptThread = new AcceptThread();
        acceptThread.start();
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
                XLog.e(TAG, "Socket listen() failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            XLog.d(TAG, "AcceptThread started");
            BluetoothSocket socket = null;
            while (true) {
                try {
                    if (serverSocket == null) {
                        XLog.e(TAG, "serverSocket is null, exiting");
                        break;
                    }
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    XLog.d(TAG, "Socket accept() failed or serverSocket closed: " + e.getMessage());
                    break;
                }

                if (socket != null) {
                    synchronized (SppServer.this) {
                        if (acceptThread != this) {
                            // 这个线程已经被取消了，关闭这个socket
                            XLog.d(TAG, "AcceptThread has been cancelled, closing accepted socket");
                            try {
                                socket.close();
                            } catch (IOException e) {
                                XLog.e(TAG, "Error closing redundant socket", e);
                            }
                            break;
                        }
                        manageConnectedSocket(socket);
                        cancel(); // 连接成功后停止监听（单连接逻辑）
                        break;
                    }
                }
            }
            XLog.d(TAG, "AcceptThread finished");
        }

        private void cancel() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                XLog.e(TAG, "serverSocket close() failed", e);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void manageConnectedSocket(BluetoothSocket socket) {
        socketWrapper = new SppSocketWrapper(socket, internalCallback);
        socketWrapper.start();
        if (callback != null) {
            callback.onConnected(socket.getRemoteDevice().getName(), socket.getRemoteDevice().getAddress());
        }
    }
}