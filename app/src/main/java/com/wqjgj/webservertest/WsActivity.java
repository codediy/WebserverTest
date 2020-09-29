package com.wqjgj.webservertest;

import android.annotation.SuppressLint;
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
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tao.admin.loglib.FileUtils;
import com.tao.admin.loglib.IConfig;
import com.tao.admin.loglib.Logger;
import com.tao.admin.loglib.TLogApplication;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
public class WsActivity extends AppCompatActivity {
    private static final int DEFAULT_PORT = 8080;
    private TextView ipText;
    private EditText portText;

    private Button serverBtn;
    private TextView statusText;

    private Button clientBtn;

    private Button logBtn;
    private TextView logText;

    private EditText timerPerText;
    private RadioGroup timerRadioGroup;


    private String baseUrl;
    private String per;
    private String perType = "1";
    private long perNum;
    private Timer timer;
    private TimerTask task;

    private Button serverPageBtn;
    private Button wsServerPageBtn;
    // webServer
    private AndroidWebSocket androidWebServer;
    private BroadcastReceiver broadcastReceiverNetworkState;
    private static boolean isStarted = false;

    // client
    private static boolean isTimer = false;
    private WsClient wsClient;

    // handler对象，用来接收消息~
    @SuppressLint("HandlerLeak")
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
        setContentView(R.layout.activity_ws);

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

        timerPerText = findViewById(R.id.per_text);
        timerRadioGroup = findViewById(R.id.timer_group);

        serverPageBtn = findViewById(R.id.server_page_btn);
        wsServerPageBtn = findViewById(R.id.ws_server_page_btn);
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
                    /*检查时间*/
                    per = timerPerText.getText().toString();
                    if (per.length() == 0) {
                        tip("请输入间隔时间");
                        return;
                    }
                    if (perType.length() == 0) {
                        tip("请输入间隔类型");
                        return;
                    }

                    long tempPer = Long.valueOf(per);
                    perNum = 0; /*秒*/
                    String perInfo = "访问间隔：每" + tempPer;
                    if (perType.equals("1")) { /*秒*/
                        perNum = tempPer * 1000;
                        perInfo += "秒";
                    }
                    if (perType.equals("2")) { /*分*/
                        perNum = tempPer * 1000 * 60;
                        perInfo += "分钟";
                    }
                    if (perType.equals("3")) { /*时*/
                        perNum = tempPer * 1000 * 60 * 60;
                        perInfo += "小时";
                    }
                    log(tempPer + "");
                    log(perType);
                    log(perNum + "");
                    log(perInfo + "");

                    if (perNum == 0) {
                        tip("请设置间隔类型信息");
                        return;
                    }
                    startClient();
                    clientBtn.setText("关闭客户端");
                    log("启动客户端，" + perInfo);
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
        timerRadioGroup.check(R.id.s_btn);
        timerRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton r = findViewById(checkedId);
                perType = r.getTag().toString();
                log("radio:" + perType + "");
            }
        });

        logText.setMovementMethod(ScrollingMovementMethod.getInstance());

        serverPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(WsActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        wsServerPageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tip("wsServer");
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
        return "ws://" + formatedIpAddress + ":";
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
                baseUrl = "ws://localhost:" + port;
                androidWebServer = new AndroidWebSocket(port);
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
        Toast.makeText(WsActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    private void log(String msg) {
        Logger.i(msg);
    }

    public void startClient(){
        try {
            new ClientTask().start();
        }catch (Exception e){
            log("关闭客户端111" + e.toString());
            e.printStackTrace();
        }
    }

    private class ClientTask extends Thread{
        @Override
        public void run() {
            Log.d("WsClientMessage","启动客户端");

            wsClient = new WsClient();
            wsClient.init(baseUrl, perNum, new WsClient.wsCallback() {
                @Override
                public void onMessage(String msg) {
                    Log.d("WsClientMessage","服务器返回信息");

                    SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm:ss");
                    Date curDate = new Date(System.currentTimeMillis());
                    String dateString = formatter.format(curDate);

                    Message message = Message.obtain();
                    message.what = 1;
                    message.obj = dateString + ":" + msg;
                    serverHandler.sendMessage(message);

                    log(msg);
                }
            });
        }
    }

    public void startTimer() {
        if (timer == null) {
            timer = new Timer();
        }
        if (task == null) {
            task = new MyTask();
        }
        if (timer != null && task != null) {
            timer.schedule(task, 1000, perNum);
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
            /*启动客户端*/

        }
    }


    @Override
    protected void onDestroy() {
        if (isStarted) {
            stopAndroidWebServer();
        }
        if (isTimer) {
            stopTimer();
        }
        if (broadcastReceiverNetworkState != null) {
            unregisterReceiver(broadcastReceiverNetworkState);
        }
        super.onDestroy();
    }
}