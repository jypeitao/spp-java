package com.microlumin.xlink.br;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


import com.microlumin.xlink.log.MLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BluetoothBrManager {
    private static final String TAG = "BluetoothBrManager";

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;

    private BluetoothA2dp bluetoothA2dp;
    private BluetoothHeadset bluetoothHeadset;
    private BluetoothProfile bluetoothA2dpSink;
    private BluetoothProfile bluetoothHeadsetClient;

    // BluetoothProfile constants for hidden profiles
    private static final int A2DP_SINK = 11;
    private static final int HEADSET_CLIENT = 16;

    private static final String ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
    private static final String ACTION_HEADSET_CLIENT_CONNECTION_STATE_CHANGED = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED";

    private final List<BrCallback> callbacks = new ArrayList<>();

    public interface BrCallback {
        void onA2dpStateChanged(BluetoothDevice device, int state);

        void onHfpStateChanged(BluetoothDevice device, int state);
    }

    public BluetoothBrManager(Context context) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        initProfiles();
        registerReceiver();
    }

    private void initProfiles() {
        if (bluetoothAdapter == null) return;

        BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        bluetoothA2dp = (BluetoothA2dp) proxy;
                        MLog.d(TAG, "A2DP service connected");
                        break;
                    case BluetoothProfile.HEADSET:
                        bluetoothHeadset = (BluetoothHeadset) proxy;
                        MLog.d(TAG, "HFP service connected");
                        break;
                    case A2DP_SINK:
                        bluetoothA2dpSink = proxy;
                        MLog.d(TAG, "A2DP Sink service connected");
                        break;
                    case HEADSET_CLIENT:
                        bluetoothHeadsetClient = proxy;
                        MLog.d(TAG, "HFP Client service connected");
                        break;
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                switch (profile) {
                    case BluetoothProfile.A2DP:
                        bluetoothA2dp = null;
                        MLog.d(TAG, "A2DP service disconnected");
                        break;
                    case BluetoothProfile.HEADSET:
                        bluetoothHeadset = null;
                        MLog.d(TAG, "HFP service disconnected");
                        break;
                    case A2DP_SINK:
                        bluetoothA2dpSink = null;
                        MLog.d(TAG, "A2DP Sink service disconnected");
                        break;
                    case HEADSET_CLIENT:
                        bluetoothHeadsetClient = null;
                        MLog.d(TAG, "HFP Client service disconnected");
                        break;
                }
            }
        };

        bluetoothAdapter.getProfileProxy(context, listener, BluetoothProfile.A2DP);
        bluetoothAdapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET);
        bluetoothAdapter.getProfileProxy(context, listener, A2DP_SINK);
        bluetoothAdapter.getProfileProxy(context, listener, HEADSET_CLIENT);
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED);
        filter.addAction(ACTION_HEADSET_CLIENT_CONNECTION_STATE_CHANGED);
        context.registerReceiver(receiver, filter);
    }

    private String stateToString(int state) {
        return switch (state) {
            case BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED";
            case BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED";
            case BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING";
            default -> "UNKNOWN";
        };
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
            int prev = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_DISCONNECTED);

            if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action) ||
                    ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED.equals(action)) {
                MLog.d(TAG, "A2DP state changed (action=" + action + "): " + device + " state: " + stateToString(state) + " prev: " + stateToString(prev));
                notifyA2dpStateChanged(device, state);
            } else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action) ||
                    ACTION_HEADSET_CLIENT_CONNECTION_STATE_CHANGED.equals(action)) {
                MLog.d(TAG, "HFP state changed (action=" + action + "): " + device + " state: " + stateToString(state) + " prev: " + stateToString(prev));
                notifyHfpStateChanged(device, state);
            }

        }
    };

    public void addCallback(BrCallback callback) {
        synchronized (callbacks) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback);
            }
        }
    }

    public void removeCallback(BrCallback callback) {
        synchronized (callbacks) {
            callbacks.remove(callback);
        }
    }

    private void notifyA2dpStateChanged(BluetoothDevice device, int state) {
        synchronized (callbacks) {
            for (BrCallback callback : callbacks) {
                callback.onA2dpStateChanged(device, state);
            }
        }
    }

    private void notifyHfpStateChanged(BluetoothDevice device, int state) {
        synchronized (callbacks) {
            for (BrCallback callback : callbacks) {
                callback.onHfpStateChanged(device, state);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public boolean connectA2dp(BluetoothDevice device) {
        if (device == null) return false;
        if (bluetoothA2dp != null) {
            MLog.d(TAG, "Connecting A2DP (Source) to " + device.getAddress());
            if (invokeConnect(bluetoothA2dp, device)) return true;
        }
        if (bluetoothA2dpSink != null) {
            MLog.d(TAG, "Connecting A2DP (Sink) to " + device.getAddress());
            return invokeConnect(bluetoothA2dpSink, device);
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    public boolean disconnectA2dp(BluetoothDevice device) {
        if (device == null) return false;
        boolean success = false;
        if (bluetoothA2dp != null) {
            MLog.d(TAG, "Disconnecting A2DP (Source) from " + device.getAddress());
            success |= invokeDisconnect(bluetoothA2dp, device);
        }
        if (bluetoothA2dpSink != null) {
            MLog.d(TAG, "Disconnecting A2DP (Sink) from " + device.getAddress());
            success |= invokeDisconnect(bluetoothA2dpSink, device);
        }
        return success;
    }

    @SuppressLint("MissingPermission")
    public boolean connectHfp(BluetoothDevice device) {
        if (device == null) return false;
        if (bluetoothHeadset != null) {
            MLog.d(TAG, "Connecting HFP (AG) to " + device.getAddress());
            if (invokeConnect(bluetoothHeadset, device)) return true;
        }
        if (bluetoothHeadsetClient != null) {
            MLog.d(TAG, "Connecting HFP (Client) to " + device.getAddress());
            return invokeConnect(bluetoothHeadsetClient, device);
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    public boolean disconnectHfp(BluetoothDevice device) {
        if (device == null) return false;
        boolean success = false;
        if (bluetoothHeadset != null) {
            MLog.d(TAG, "Disconnecting HFP (AG) from " + device.getAddress());
            success |= invokeDisconnect(bluetoothHeadset, device);
        }
        if (bluetoothHeadsetClient != null) {
            MLog.d(TAG, "Disconnecting HFP (Client) from " + device.getAddress());
            success |= invokeDisconnect(bluetoothHeadsetClient, device);
        }
        return success;
    }

    private boolean invokeConnect(BluetoothProfile proxy, BluetoothDevice device) {
        try {
            Method connect = proxy.getClass().getDeclaredMethod("connect", BluetoothDevice.class);
            connect.setAccessible(true);
            return (Boolean) connect.invoke(proxy, device);
        } catch (NoSuchMethodException e) {
            MLog.e(TAG, "Method connect not found for " + proxy.getClass().getName(), e);
            return false;
        } catch (Exception e) {
            MLog.e(TAG, "Error invoking connect on " + proxy.getClass().getName(), e);
            return false;
        }
    }

    private boolean invokeDisconnect(BluetoothProfile proxy, BluetoothDevice device) {
        try {
            Method disconnect = proxy.getClass().getDeclaredMethod("disconnect", BluetoothDevice.class);
            disconnect.setAccessible(true);
            return (Boolean) disconnect.invoke(proxy, device);
        } catch (NoSuchMethodException e) {
            MLog.e(TAG, "Method disconnect not found for " + proxy.getClass().getName(), e);
            return false;
        } catch (Exception e) {
            MLog.e(TAG, "Error invoking disconnect on " + proxy.getClass().getName(), e);
            return false;
        }
    }

    public void release() {
        context.unregisterReceiver(receiver);
        if (bluetoothAdapter != null) {
            if (bluetoothA2dp != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp);
            }
            if (bluetoothHeadset != null) {
                bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
            }
            if (bluetoothA2dpSink != null) {
                bluetoothAdapter.closeProfileProxy(A2DP_SINK, bluetoothA2dpSink);
            }
            if (bluetoothHeadsetClient != null) {
                bluetoothAdapter.closeProfileProxy(HEADSET_CLIENT, bluetoothHeadsetClient);
            }
        }
        synchronized (callbacks) {
            callbacks.clear();
        }
    }
}