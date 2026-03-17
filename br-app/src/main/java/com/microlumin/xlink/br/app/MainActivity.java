package com.microlumin.xlink.br.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.microlumin.xlink.br.BluetoothBrManager;
import com.microlumin.xlink.log.MLog;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private BluetoothBrManager bluetoothBrManager;
    private TextView tvStatus;
    private EditText etMac;

    private int a2dpState = BluetoothProfile.STATE_DISCONNECTED;
    private int hfpState = BluetoothProfile.STATE_DISCONNECTED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        etMac = findViewById(R.id.et_mac);

        bluetoothBrManager = new BluetoothBrManager(this);
        bluetoothBrManager.addCallback(new BluetoothBrManager.BrCallback() {
            @Override
            public void onA2dpStateChanged(BluetoothDevice device, int state) {
                a2dpState = state;
                updateStatusUI();
            }

            @Override
            public void onHfpStateChanged(BluetoothDevice device, int state) {
                hfpState = state;
                updateStatusUI();
            }
        });

        findViewById(R.id.btn_connect_a2dp).setOnClickListener(v -> connectA2dp());
        findViewById(R.id.btn_disconnect_a2dp).setOnClickListener(v -> disconnectA2dp());
        findViewById(R.id.btn_connect_hfp).setOnClickListener(v -> connectHfp());
        findViewById(R.id.btn_disconnect_hfp).setOnClickListener(v -> disconnectHfp());

        updateStatusUI();
    }

    private void updateStatusUI() {
        runOnUiThread(() -> {
            String status = "A2DP: " + stateToString(a2dpState) + "\n" +
                            "HFP: " + stateToString(hfpState);
            tvStatus.setText(status);
        });
    }

    private String stateToString(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED: return "Connected";
            case BluetoothProfile.STATE_CONNECTING: return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTING: return "Disconnecting";
            case BluetoothProfile.STATE_DISCONNECTED: return "Disconnected";
            default: return "Unknown (" + state + ")";
        }
    }

    private BluetoothDevice getTargetDevice() {
        String mac = etMac.getText().toString().trim().toUpperCase();
        if (mac.isEmpty()) {
            Toast.makeText(this, "Please enter MAC address", Toast.LENGTH_SHORT).show();
            return null;
        }
        try {
            return BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Invalid MAC address", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void connectA2dp() {
        BluetoothDevice device = getTargetDevice();
        if (device != null) {
            boolean success = bluetoothBrManager.connectA2dp(device);
            MLog.d(TAG, "Connect A2DP " + (success ? "initiated" : "failed"));
        }
    }

    private void disconnectA2dp() {
        BluetoothDevice device = getTargetDevice();
        if (device != null) {
            boolean success = bluetoothBrManager.disconnectA2dp(device);
            MLog.d(TAG, "Disconnect A2DP " + (success ? "initiated" : "failed"));
        }
    }

    private void connectHfp() {
        BluetoothDevice device = getTargetDevice();
        if (device != null) {
            boolean success = bluetoothBrManager.connectHfp(device);
            MLog.d(TAG, "Connect HFP " + (success ? "initiated" : "failed"));
        }
    }

    private void disconnectHfp() {
        BluetoothDevice device = getTargetDevice();
        if (device != null) {
            boolean success = bluetoothBrManager.disconnectHfp(device);
            MLog.d(TAG, "Disconnect HFP " + (success ? "initiated" : "failed"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothBrManager != null) {
            bluetoothBrManager.release();
        }
    }
}