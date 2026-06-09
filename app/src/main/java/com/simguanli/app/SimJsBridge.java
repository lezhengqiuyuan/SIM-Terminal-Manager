package com.simguanli.app;

import android.util.Base64;
import android.webkit.JavascriptInterface;

public class SimJsBridge {
    private final SimNetworkInfo info;
    public SimJsBridge(SimNetworkInfo info) { this.info = info; }

    @JavascriptInterface public String getAllInfo() { return info.getAllInfo().toString(); }
    @JavascriptInterface public String getSimInfo() { return info.getSimInfo().toString(); }
    @JavascriptInterface public String getNetworkInfo() { return info.getNetworkTypeInfo().toString(); }
    @JavascriptInterface public String getSignalInfo() { return info.getSignalInfo().toString(); }
    @JavascriptInterface public String getCellInfo() { return info.getCellInfo().toString(); }
    @JavascriptInterface public String getQosInfo() { return info.getQosInfo().toString(); }
    @JavascriptInterface public String getHiddenInfo() { return info.getHiddenServiceState().toString(); }
    @JavascriptInterface public String getDecoderStatus() { return info.getNativeDecoderStatus().toString(); }
    @JavascriptInterface public String decodeRaw(String base64, int rat) {
        try { byte[] d = Base64.decode(base64, Base64.DEFAULT); String r = info.decodeRawMessage(d, rat); return r != null ? r : "null"; }
        catch (Exception e) { return "null"; }
    }
}
