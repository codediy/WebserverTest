package com.wqjgj.webservertest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tao.admin.loglib.FileUtils;
import com.tao.admin.loglib.IConfig;
import com.tao.admin.loglib.Logger;
import com.tao.admin.loglib.TLogApplication;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;
import com.zhy.http.okhttp.log.LoggerInterceptor;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class MainActivity extends AppCompatActivity {
    private static final int DEFAULT_PORT = 8080;
    private TextView ipText;
    private EditText portText;

    private Button serverBtn;
    private TextView statusText;

    private Button clientBtn;

    private Button logBtn;
    private TextView logText;

    private String baseUrl;
    private Timer timer;
    private TimerTask task;


    // webServer
    private AndroidWebServer androidWebServer;
    private BroadcastReceiver broadcastReceiverNetworkState;
    private static boolean isStarted = false;

    // client
    private static boolean isTimer = false;

    // handler对象，用来接收消息~
    private Handler serverHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            tip(msg.obj + "");
            super.handleMessage(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        initData();

        TLogApplication.initialize(this);
        IConfig.getInstance().isShowLog(true)//是否在logcat中打印log,默认不打印
                .isWriteLog(true)//是否在文件中记录，默认不记录
                .fileSize(100000)//日志文件的大小，默认0.1M,以bytes为单位
                .tag("webServer");//logcat 日志过滤tag
    }

    private void initView() {
        ipText = findViewById(R.id.ip_text);
        portText = findViewById(R.id.port_text);
        serverBtn = findViewById(R.id.server_btn);
        statusText = findViewById(R.id.status_text);
        clientBtn = findViewById(R.id.client_btn);
        logBtn = findViewById(R.id.log_btn);
        logText = findViewById(R.id.log_text);
    }

    private void initData() {
        setIpAccess();
        statusText.setText("服务器待启动");
        clientBtn.setEnabled(false);
        initBroadcastReceiverNetworkStateChanged();
        serverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnectedInWifi()) {
                    if (!isStarted && startAndroidWebServer()) {
                        isStarted = true;
                        statusText.setText("服务器已启动");
                        serverBtn.setText("关闭服务器");
                        portText.setEnabled(false);
                        clientBtn.setEnabled(true);
                        log("服务器启动");
                    } else if (stopAndroidWebServer()) {
                        isStarted = false;
                        statusText.setText("服务器已关闭");
                        serverBtn.setText("启动服务器");
                        portText.setEnabled(true);
                        clientBtn.setEnabled(false);
                        log("服务器已关闭");
                        if (isTimer) {
                            stopTimer();
                            isTimer = false;
                        }
                    }
                } else {
                    tip("请使用wifi链接网络");
                }
            }
        });

        clientBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isTimer) {
                    stopTimer();
                    clientBtn.setText("启动客户端");
                    log("关闭客户端");
                    isTimer = false;
                } else {
                    startTimer();
                    clientBtn.setText("关闭客户端");
                    log("启动客户端");
                    isTimer = true;
                }
            }
        });

        logBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String log = FileUtils.readLogText();
                logText.setText(log);
            }
        });
    }

    private void setIpAccess() {
        ipText.setText(getIpAccess());
    }

    private String getIpAccess() {
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        final String formatedIpAddress = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        return "http://" + formatedIpAddress + ":";
    }

    private int getPortFromEditText() {
        String valueEditText = portText.getText().toString();
        return (valueEditText.length() > 0) ? Integer.parseInt(valueEditText) : DEFAULT_PORT;
    }

    public boolean isConnectedInWifi() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        NetworkInfo networkInfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()
                && wifiManager.isWifiEnabled() && networkInfo.getTypeName().equals("WIFI")) {
            return true;
        }
        return false;
    }

    //启动服务器
    private boolean startAndroidWebServer() {
        if (!isStarted) {
            int port = getPortFromEditText();
            try {
                if (port == 0) {
                    throw new Exception();
                }
                baseUrl = "http://localhost:" + port;
                androidWebServer = new AndroidWebServer(port);
                androidWebServer.start();
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                tip("端口 " + port + " 无法启用, 请切换到 1000 到 9999之间其他端口.");
            }
        }
        return false;
    }

    //关闭服务器
    private boolean stopAndroidWebServer() {
        if (isStarted && androidWebServer != null) {
            androidWebServer.stop();
            return true;
        }
        return false;
    }

    private void initBroadcastReceiverNetworkStateChanged() {
        final IntentFilter filters = new IntentFilter();
        filters.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filters.addAction("android.net.wifi.STATE_CHANGE");
        broadcastReceiverNetworkState = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                setIpAccess();
            }
        };
        super.registerReceiver(broadcastReceiverNetworkState, filters);
    }

    public void tip(String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    private void log(String msg) {
        Logger.i(msg);
    }

    public void startTimer() {
        if (timer == null) {
            timer = new Timer();
        }
        if (task == null) {
            task = new MyTask();
        }

        if (timer != null && task != null) {
            timer.schedule(task, 1000, 1000 * 10);
        }
    }

    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void restartTimer() {
        if (timer == null) {
            startTimer();
        }
    }

    class MyTask extends TimerTask {
        @Override
        public void run() {
            OkHttpClient okHttpClient = new OkHttpClient();
            Request request = new Request.Builder().url(baseUrl).build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                String res;
                if (response.isSuccessful()) {
                    res = body == null ? "" : body.string();
                    Log.d("success:{}", res);
                } else {
                    res = response.code() + "";
                    Log.e("error", res);
                }
                SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm:ss");
                Date curDate = new Date(System.currentTimeMillis());
                String dateString = formatter.format(curDate);

                Message message = Message.obtain();
                message.what = 1;
                message.obj = dateString + ":" + res;
                serverHandler.sendMessage(message);
                log(res);
            } catch (IOException e) {
                log("关闭客户端" + e.toString());
                e.printStackTrace();
            }
        }
    }
}