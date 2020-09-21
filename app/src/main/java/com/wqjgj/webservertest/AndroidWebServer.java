package com.wqjgj.webservertest;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;

/**
 * Created by Mikhael LOPEZ on 14/12/2015.
 */
public class AndroidWebServer extends NanoHTTPD {

    public AndroidWebServer(int port) {
        super(port);
    }

    public AndroidWebServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d("SERVER","服务器链接信息");
        String uri = session.getUri();
        StringBuilder sb = new StringBuilder();
        sb.append("服务器响应");
        return newFixedLengthResponse(sb.toString());
    }
}
