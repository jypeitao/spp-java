package com.microlumin.xlink.spp.client;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

public class SppClient {
    private static final String TAG = "SppClient";

    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private ConnectThread connectThread;
    private SppSocketWrapper socketWrapper;
    private final SppCallback callback;
    private SppState state = SppState.DISCONNECTED;
    private BluetoothDevice connectedDevice;

    public SppClient(Context context, SppCallback callback) {
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

    public synchronized void connect(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address.toUpperCase());
        connect(device);
    }

    public synchronized void connect(BluetoothDevice device) {
        if (state == SppState.CONNECTING || state == SppState.CONNECTED) {
            if (connectedDevice != null && connectedDevice.getAddress().equals(device.getAddress())) {
                XLog.d(TAG, "Already " + state + " to " + device.getAddress() + ", ignore connect request");
                return;
            }
            XLog.d(TAG, "Connecting/Connected to another device, disconnecting first");
            disconnect();
        }
        connectedDevice = device;
        setState(SppState.CONNECTING, device);
        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    public synchronized void disconnect() {
        if (state == SppState.DISCONNECTED || state == SppState.DISCONNECTING) {
            return;
        }
        setState(SppState.DISCONNECTING);
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (socketWrapper != null) {
            socketWrapper.stop();
            socketWrapper = null;
        }
        setState(SppState.DISCONNECTED);
    }

    public synchronized boolean send(byte[] data) {
        if (socketWrapper != null) {
            return socketWrapper.send(data);
        }
        return false;
    }

    public synchronized boolean sendPacket(byte[] payload) {
        if (socketWrapper != null) {
            return socketWrapper.sendPacket(payload);
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
                XLog.e(TAG, "Socket create() failed", e);
            }
            socket = tmp;
        }

        @SuppressLint("MissingPermission")
        public void run() {
            BluetoothDevice device = socket.getRemoteDevice();
            if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                XLog.d(TAG, "Device not bonded, initiating pairing and waiting for broadcast...");
                final Object bondLock = new Object();
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                            BluetoothDevice bondedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            if (bondedDevice != null && bondedDevice.getAddress().equals(device.getAddress())) {
                                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                                XLog.d(TAG, "Device bonded (state: " + state + ")");
                                if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) {
                                    synchronized (bondLock) {
                                        bondLock.notifyAll();
                                    }
                                }
                            }
                        }
                    }
                };

                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                context.registerReceiver(receiver, filter);

                try {
                    if (device.createBond()) {
                        synchronized (bondLock) {
                            bondLock.wait(60000); // Wait up to 60 seconds
                        }
                    }
                } catch (InterruptedException e) {
                    XLog.e(TAG, "Interrupted while waiting for bonding", e);
                } finally {
                    try {
                        context.unregisterReceiver(receiver);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }

            // 检查配对状态，只有配对成功才继续连接
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                XLog.w(TAG, "Device not bonded (state: " + device.getBondState() + "), skipping connect");
                synchronized (SppClient.this) {
                    if (connectThread == this) {
                        setState(SppState.DISCONNECTED);
                        if (callback != null) {
                            callback.onError("Device pairing failed or cancelled");
                        }
                    }
                }
                return;
            }

            try {
                if (bluetoothAdapter.isDiscovering()) {
                    XLog.i(TAG, "cancelDiscovery");
                    bluetoothAdapter.cancelDiscovery();
                }
            } catch (SecurityException e) {
                XLog.e(TAG, "Missing BLUETOOTH_SCAN permission to check/cancel discovery", e);
            }

            try {
                if (socket == null) return;
                socket.connect();
            } catch (IOException e) {
                XLog.e(TAG, "Socket connect() failed", e);
                try {
                    socket.close();
                } catch (IOException e2) {
                    XLog.e(TAG, "unable to close() socket during connection failure", e2);
                }
                synchronized (SppClient.this) {
                    if (connectThread == this) {
                        setState(SppState.DISCONNECTED);
                        if (callback != null) callback.onError("Connection failed: " + e.getMessage());
                    }
                }
                return;
            }

            synchronized (SppClient.this) {
                if (connectThread != this) {
                    // This thread was cancelled or replaced
                    XLog.d(TAG, "ConnectThread has been cancelled, closing socket");
                    try {
                        socket.close();
                    } catch (IOException e) {
                        XLog.e(TAG, "Error closing redundant socket", e);
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
                XLog.e(TAG, "socket close() failed", e);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void manageConnectedSocket(BluetoothSocket socket) {
        socketWrapper = new SppSocketWrapper(socket, new SppCallback() {
            @Override
            public void onStateChanged(SppState state, String deviceName, String deviceAddress) {
                if (state == SppState.DISCONNECTED) {
                    synchronized (SppClient.this) {
                        setState(SppState.DISCONNECTED);
                        connectedDevice = null;
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
        });
        socketWrapper.start();
        BluetoothDevice device = socket.getRemoteDevice();
        setState(SppState.CONNECTED, device);
    }
}