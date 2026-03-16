package com.microlumin.xlink.spp.server;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import com.microlumin.xlink.spp.common.SppState;

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
    private SppState state = SppState.DISCONNECTED;
    private BluetoothDevice connectedDevice;

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
        public void onStateChanged(SppState state, String deviceName, String deviceAddress) {
            if (state == SppState.DISCONNECTED) {
                synchronized (SppServer.this) {
                    setState(SppState.DISCONNECTED);
                    connectedDevice = null;
                    if (isStarted && bluetoothAdapter.isEnabled()) {
                        XLog.d(TAG, "Re-starting AcceptThread after disconnection");
                        startAcceptThread();
                    }
                }
            }
        }

        @Override
        public void onPacketReceived(byte[] payload) {
            if (callback != null) callback.onPacketReceived(payload);
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

    public synchronized SppState getState() {
        return state;
    }

    private synchronized void setState(SppState state) {
        setState(state, connectedDevice);
    }

    @SuppressLint("MissingPermission")
    private synchronized void setState(SppState state, BluetoothDevice device) {
        if (device != null) {
            connectedDevice = device;
            setState(state, device.getName(), device.getAddress());
        } else {
            setState(state, null, null);
        }
    }

    private synchronized void setState(SppState state, String deviceName, String deviceAddress) {
        if (this.state != state) {
            XLog.d(TAG, "State changed: " + this.state + " -> " + state);
            this.state = state;
            if (callback != null) {
                callback.onStateChanged(state, deviceName, deviceAddress);
            }
        }
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

        setState(SppState.DISCONNECTING);

        try {
            context.unregisterReceiver(bluetoothStateReceiver);
        } catch (Exception e) {
            XLog.e(TAG, "Error unregistering receiver", e);
        }

        stopThreads();
        setState(SppState.DISCONNECTED);
    }

    /**
     * 主动断开当前连接，断开后会自动重新进入监听状态 (如果 isStarted 为 true)
     */
    public synchronized void disconnect() {
        XLog.d(TAG, "disconnect()");
        if (socketWrapper != null) {
            socketWrapper.stop();
            socketWrapper = null;
        }
        // 由于 socketWrapper.stop() 可能不会立即触发 internalCallback 的状态回调（如果是由本地主动调用的），
        // 我们在这里手动设置一次状态，确保 UI 和 内部逻辑同步。
        setState(SppState.DISCONNECTED);
        connectedDevice = null;

        // 如果服务器仍在运行且蓝牙开启，重新开始监听
        if (isStarted && bluetoothAdapter.isEnabled()) {
            XLog.d(TAG, "Re-starting AcceptThread after manual disconnection");
            startAcceptThread();
        }
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

    public synchronized boolean sendPacket(byte[] payload) {
        return sendPacket(payload, true);
    }

    public synchronized boolean sendPacket(byte[] payload, boolean flush) {
        if (socketWrapper != null) {
            return socketWrapper.sendPacket(payload, flush);
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
        BluetoothDevice device = socket.getRemoteDevice();
        setState(SppState.CONNECTED, device);
    }
}