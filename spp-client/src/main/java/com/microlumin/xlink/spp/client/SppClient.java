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

import java.io.IOException;

public class SppClient {
    private static final String TAG = "SppClient";

    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private ConnectThread connectThread;
    private SppSocketWrapper socketWrapper;
    private final SppCallback callback;

    public SppClient(Context context, SppCallback callback) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.callback = callback;
    }

    public synchronized void connect(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address.toUpperCase());
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
                        if (callback != null) callback.onError("Device pairing failed or cancelled");
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
        socketWrapper = new SppSocketWrapper(socket, callback);
        socketWrapper.start();
        if (callback != null) {
            callback.onConnected(socket.getRemoteDevice().getName(), socket.getRemoteDevice().getAddress());
        }
    }
}