package com.wqjgj.webservertest;

import android.util.Log;

import com.google.gson.Gson;
import com.orhanobut.hawk.Hawk;
import com.wqjgj.webservertest.RxTimer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WsClient {
    private static final String TAG = "WebSocketClient";
    private boolean isStart = true;
    private String webSocketUrl = "ws://io.wqjgj.com:9786";
    private WebSocket mSocket;
    private RxTimer rt;
    private long HearTime = 10 * 1000;
    private long currentTime = 0;
    private wsCallback clientCb;

    public interface wsCallback {
        void onMessage(String msg);
    }

    public void init(String url, long hearTime, wsCallback cb) {
        this.webSocketUrl = url;
        this.clientCb = cb;
        this.HearTime = hearTime;

        if (!isStart || webSocketUrl.length() == 0) {
            return;
        }

        OkHttpClient mOkHttpClient = new OkHttpClient.Builder()
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .connectTimeout(3, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(webSocketUrl)
                .build();

        LockAppWebSocketListener listener = new LockAppWebSocketListener();
        mOkHttpClient.newWebSocket(request, listener);
        mOkHttpClient.dispatcher().executorService().shutdown();
    }

    private void sendHeart() {
        if (mSocket != null) {
            mSocket.send(HearData());
        }
    }

    private String HearData() {
        Map<String, String> p = new HashMap<>();
        p.put("info", "heart");
        return sendData(p);
    }

    private String sendData(Map<String, String> params) {
        Map<String, Object> p = new HashMap<>();
        p.put("option", params);
        return new Gson().toJson(p);
    }

    private final class LockAppWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            super.onOpen(webSocket, response);
            Log.d(TAG, "链接成功");
            /*记录接口*/
            mSocket = webSocket;
            sendHeart();
            /*定时心跳*/
            rt = new RxTimer();
            rt.interval(HearTime, new RxTimer.RxAction() {
                @Override
                public void action(long number) {
                    currentTime = currentTime + HearTime;
                    sendHeart();
                }
            });
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            Log.d(TAG, "获得消息:" + text);
            clientCb.onMessage(text);
            super.onMessage(webSocket, text);
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            super.onClosed(webSocket, code, reason);
        }

        @Override
        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            super.onClosing(webSocket, code, reason);
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            super.onFailure(webSocket, t, response);
            Log.d(TAG, "连接失败");
            t.printStackTrace();
        }
    }
}
