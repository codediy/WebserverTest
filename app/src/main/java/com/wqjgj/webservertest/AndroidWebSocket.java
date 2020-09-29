package com.wqjgj.webservertest;

import android.util.Log;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

public class AndroidWebSocket extends NanoWSD {
    public AndroidWebSocket(int port) {
        super(port);
    }

    public AndroidWebSocket(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession ihttpSession) {
        return new WsdSocket(ihttpSession);
    }

    private static class WsdSocket extends WebSocket {
        public WsdSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        protected void onOpen() {
            Log.d("WsdSocket","Ws服务器链接信息");
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {

        }

        //override onOpen, onClose, onPong and onException methods

        @Override
        protected void onMessage(WebSocketFrame webSocketFrame) {
            try {
                send(webSocketFrame.getTextPayload() + " to you");
            } catch (IOException e) {
                // handle
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {

        }

        @Override
        protected void onException(IOException exception) {

        }
    }
}
