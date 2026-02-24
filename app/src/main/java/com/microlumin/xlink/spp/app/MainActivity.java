package com.microlumin.xlink.spp.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

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
import com.microlumin.xlink.spp.common.SppState;
import com.microlumin.xlink.spp.client.SppClient;
import com.microlumin.xlink.spp.server.SppServer;

public class MainActivity extends AppCompatActivity {

    private RadioGroup rgMode;
    private RadioButton rbServer, rbClient;
    private EditText etMac, etMessage;
    private Button btnStart, btnSend, btnStressTest;
    private TextView tvStatus, tvSpeed;
    private LinearLayout containerMessages;
    private ScrollView scrollMessages;

    private SppServer sppServer;
    private SppClient sppClient;

    private volatile boolean isStressTesting = false;
    private final Handler speedHandler = new Handler(Looper.getMainLooper());
    private Runnable speedRunnable;
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private long lastBytesSent = 0;
    private long lastBytesReceived = 0;
    private final Random random = new Random();

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
        public void onStateChanged(SppState state, String deviceName, String deviceAddress) {
            runOnUiThread(() -> {
                updateStatusText();
                // 根据状态更新按钮文案（尤其是客户端断开后应显示“连接客户端”）
                if (currentMode == Mode.CLIENT) {
                    switch (state) {
                        case CONNECTED:
                        case CONNECTING:
                        case DISCONNECTING:
                            btnStart.setText("断开");
                            break;
                        case DISCONNECTED:
                        default:
                            stopAll();
                            btnStart.setText("连接客户端");
                            break;
                    }
                }
                if (state == SppState.CONNECTED) {
                    addMessage("[系统] 已连接到 " + deviceName + " (" + deviceAddress + ")");
                } else if (state == SppState.DISCONNECTED) {
                    addMessage("[系统] 连接已断开");
                }
            });
        }

        @Override
        public void onPacketReceived(byte[] payload) {
            bytesReceived.addAndGet(payload.length);
            runOnUiThread(() -> {
                if (!isStressTesting) {
                    addMessage("对方(包): " + new String(payload));
                }
            });
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
        tvSpeed = findViewById(R.id.tv_speed);
        containerMessages = findViewById(R.id.container_messages);
        scrollMessages = findViewById(R.id.scroll_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send);
        btnStressTest = findViewById(R.id.btn_stress_test);
    }

    private void initUi() {
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_server) {
                currentMode = Mode.SERVER;
                etMac.setVisibility(View.GONE);
                btnStart.setText("启动服务端");
                updateStatusText();
                addMessage("[系统] 已切换到服务端模式");
            } else {
                currentMode = Mode.CLIENT;
                etMac.setVisibility(View.VISIBLE);
                btnStart.setText("连接客户端");
                updateStatusText();
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
                updateStatusText();
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
            sendData(text.getBytes(), true);
            etMessage.setText("");
        });

        btnStressTest.setOnClickListener(v -> toggleStressTest());
    }

    private void sendData(byte[] data, boolean showInUi) {
        boolean ok = false;
        if (currentMode == Mode.SERVER && sppServer != null) {
            ok = sppServer.sendPacket(data);
        } else if (currentMode == Mode.CLIENT && sppClient != null) {
            ok = sppClient.sendPacket(data);
        }
        if (ok) {
            bytesSent.addAndGet(data.length);
            if (showInUi && !isStressTesting) {
                addMessage("我: " + new String(data));
            }
        } else if (showInUi) {
            Toast.makeText(this, "发送失败（未连接或通道异常）", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleStressTest() {
        if (isStressTesting) {
            stopStressTest();
        } else {
            if (!isConnected()) {
                Toast.makeText(this, "未连接，无法开始压力测试", Toast.LENGTH_SHORT).show();
                return;
            }
            startStressTest();
        }
    }

    private boolean isConnected() {
        if (currentMode == Mode.SERVER && sppServer != null) {
            return sppServer.getState() == SppState.CONNECTED;
        } else if (currentMode == Mode.CLIENT && sppClient != null) {
            return sppClient.getState() == SppState.CONNECTED;
        }
        return false;
    }

    private void startStressTest() {
        isStressTesting = true;
        btnStressTest.setText("停止压测");
        tvSpeed.setVisibility(View.VISIBLE);
        bytesSent.set(0);
        bytesReceived.set(0);
        lastBytesSent = 0;
        lastBytesReceived = 0;

        speedRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isStressTesting) return;

                long currentSent = bytesSent.get();
                long currentReceived = bytesReceived.get();
                double sSpeed = (currentSent - lastBytesSent) / 1024.0;
                double rSpeed = (currentReceived - lastBytesReceived) / 1024.0;
                lastBytesSent = currentSent;
                lastBytesReceived = currentReceived;

                tvSpeed.setText(String.format("发送: %.1fKB/s | 接收: %.1fKB/s", sSpeed, rSpeed));

                speedHandler.postDelayed(this, 1000);
            }
        };
        speedHandler.postDelayed(speedRunnable, 1000);

        new Thread(() -> {
            while (isStressTesting && isConnected()) {
                int size = random.nextInt(4096) + 1;
                byte[] data = new byte[size];
                random.nextBytes(data);
                sendData(data, false);
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
            if (isStressTesting) {
                runOnUiThread(this::stopStressTest);
            }
        }).start();
    }

    private void stopStressTest() {
        isStressTesting = false;
        btnStressTest.setText("压力测试");
        tvSpeed.setVisibility(View.INVISIBLE);
        tvSpeed.setText("");
        if (speedRunnable != null) {
            speedHandler.removeCallbacks(speedRunnable);
            speedRunnable = null;
        }
    }

    private boolean isRunning() {
        return sppServer != null || sppClient != null;
    }

    private void startServer() {
        stopAll();
        sppServer = new SppServer(this, callback);
        sppServer.start();
        updateStatusText();
        btnStart.setText("停止");
        addMessage("[系统] 服务端已启动，等待连接");
    }

    private void startClient(@NonNull String mac) {
        stopAll();
        sppClient = new SppClient(this, callback);
        sppClient.connect(mac);
        updateStatusText();
        btnStart.setText("断开");
        addMessage("[系统] 正在连接对端：" + mac);
    }

    private void updateStatusText() {
        SppState state = SppState.DISCONNECTED;
        if (currentMode == Mode.SERVER && sppServer != null) {
            state = sppServer.getState();
        } else if (currentMode == Mode.CLIENT && sppClient != null) {
            state = sppClient.getState();
        }

        String stateStr;
        switch (state) {
            case CONNECTING:
                stateStr = "正在连接...";
                break;
            case CONNECTED:
                stateStr = "已连接";
                break;
            case DISCONNECTING:
                stateStr = "正在断开...";
                break;
            case DISCONNECTED:
            default:
                stateStr = "未连接";
                break;
        }
        tvStatus.setText("状态: " + stateStr);
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