package com.microlumin.xlink.spp.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.microlumin.xlink.spp.common.SppCallback;
import com.microlumin.xlink.spp.client.SppClient;
import com.microlumin.xlink.spp.server.SppServer;

public class MainActivity extends AppCompatActivity {

    private RadioGroup rgMode;
    private RadioButton rbServer, rbClient;
    private EditText etMac, etMessage;
    private Button btnStart, btnSend;
    private TextView tvStatus;
    private LinearLayout containerMessages;
    private ScrollView scrollMessages;

    private SppServer sppServer;
    private SppClient sppClient;

    private enum Mode { SERVER, CLIENT }
    private Mode currentMode = Mode.SERVER;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // 简单反馈：如果关键权限被拒绝则提示
                if (!hasBluetoothConnectPermission()) {
                    Toast.makeText(this, "缺少蓝牙权限，功能可能不可用", Toast.LENGTH_LONG).show();
                }
            });

    private final SppCallback callback = new SppCallback() {
        @Override
        public void onConnected(String deviceName, String deviceAddress) {
            runOnUiThread(() -> {
                tvStatus.setText("已连接: " + deviceName + " (" + deviceAddress + ")");
                addMessage("[系统] 已连接到 " + deviceName + " (" + deviceAddress + ")");
            });
        }

        @Override
        public void onDisconnected() {
            runOnUiThread(() -> {
                tvStatus.setText("已断开");
                addMessage("[系统] 连接已断开");
            });
        }

        @Override
        public void onDataReceived(byte[] data) {
            runOnUiThread(() -> addMessage("对方: " + new String(data)));
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                addMessage("[错误] " + message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        initUi();
        requestBtPermissionsIfNeeded();
    }

    private void bindViews() {
        rgMode = findViewById(R.id.rg_mode);
        rbServer = findViewById(R.id.rb_server);
        rbClient = findViewById(R.id.rb_client);
        etMac = findViewById(R.id.et_mac);
        btnStart = findViewById(R.id.btn_start);
        tvStatus = findViewById(R.id.tv_status);
        containerMessages = findViewById(R.id.container_messages);
        scrollMessages = findViewById(R.id.scroll_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
    }

    private void initUi() {
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_server) {
                currentMode = Mode.SERVER;
                etMac.setVisibility(View.GONE);
                btnStart.setText("启动服务端");
                tvStatus.setText("未连接");
                addMessage("[系统] 已切换到服务端模式");
            } else {
                currentMode = Mode.CLIENT;
                etMac.setVisibility(View.VISIBLE);
                btnStart.setText("连接客户端");
                tvStatus.setText("未连接");
                addMessage("[系统] 已切换到客户端模式");
            }
            stopAll();
        });

        btnStart.setOnClickListener(v -> {
            if (!hasBluetoothConnectPermission()) {
                requestBtPermissionsIfNeeded();
                return;
            }
            if (isRunning()) {
                stopAll();
                tvStatus.setText("未连接");
                btnStart.setText(currentMode == Mode.SERVER ? "启动服务端" : "连接客户端");
                addMessage("[系统] 已停止");
                return;
            }
            if (currentMode == Mode.SERVER) {
                startServer();
            } else {
                String mac = etMac.getText().toString().trim();
                if (TextUtils.isEmpty(mac)) {
                    Toast.makeText(this, "请输入对端设备 MAC 地址", Toast.LENGTH_SHORT).show();
                    return;
                }
                startClient(mac);
            }
        });

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString();
            if (TextUtils.isEmpty(text)) return;
            boolean ok = false;
            byte[] data = text.getBytes();
            if (currentMode == Mode.SERVER && sppServer != null) {
                ok = sppServer.send(data);
            } else if (currentMode == Mode.CLIENT && sppClient != null) {
                ok = sppClient.send(data);
            }
            if (ok) {
                addMessage("我: " + text);
                etMessage.setText("");
            } else {
                Toast.makeText(this, "发送失败（未连接或通道异常）", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isRunning() {
        return sppServer != null || sppClient != null;
    }

    private void startServer() {
        stopAll();
        sppServer = new SppServer(callback);
        sppServer.start();
        tvStatus.setText("等待客户端连接...");
        btnStart.setText("停止");
        addMessage("[系统] 服务端已启动，等待连接");
    }

    private void startClient(@NonNull String mac) {
        stopAll();
        sppClient = new SppClient(callback);
        sppClient.connect(mac);
        tvStatus.setText("正在连接 " + mac + "...");
        btnStart.setText("断开");
        addMessage("[系统] 正在连接对端：" + mac);
    }

    private void stopAll() {
        if (sppServer != null) {
            sppServer.stop();
            sppServer = null;
        }
        if (sppClient != null) {
            sppClient.disconnect();
            sppClient = null;
        }
    }

    private void addMessage(String msg) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        containerMessages.addView(tv);
        scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
    }

    private boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // 低版本无需此动态权限
    }

    private void requestBtPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothConnectPermission()) {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAll();
    }
}