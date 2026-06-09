package com.simguanli.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.*;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.Executors;

public class SimNetworkInfo {
    private static final String TAG = "SimNetworkInfo";
    private final Context ctx;
    private final TelephonyManager tm;
    private final ConnectivityManager cm;
    private int lastQci = -1;

    public SimNetworkInfo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        this.cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @SuppressLint("MissingPermission")
    public JSONObject getSimInfo() {
        JSONObject j = new JSONObject();
        try {
            j.put("carrierName", s(tm.getNetworkOperatorName()));
            j.put("simOperator", s(tm.getSimOperator()));
            j.put("simState", simSt(tm.getSimState()));
            j.put("isRoaming", tm.isNetworkRoaming());
            if (hasP()) {
                j.put("iccid", s(tm.getSimSerialNumber()));
                j.put("imsi", s(tm.getSubscriberId()));
                j.put("imei1", s(imei(0)));
                j.put("imei2", s(imei(1)));
            }
        } catch (Exception e) {}
        return j;
    }

    public JSONObject getNetworkTypeInfo() {
        JSONObject j = new JSONObject();
        try {
            int dt = tm.getDataNetworkType();
            j.put("type", dt);
            j.put("name", nn(dt));
            j.put("gen", dt == 20 ? "5G" : dt == 19 ? "4G" : dt >= 3 ? "3G" : "2G");
        } catch (Exception e) {}
        return j;
    }

    public JSONObject getSignalInfo() {
        JSONObject j = new JSONObject();
        try {
            SignalStrength ss = tm.getSignalStrength();
            if (ss != null) j.put("raw", ss.toString());
        } catch (Exception e) {}
        return j;
    }

    @SuppressLint("MissingPermission")
    public JSONObject getCellInfo() {
        JSONObject j = new JSONObject();
        try {
            if (Build.VERSION.SDK_INT >= 29 && !hasL()) { j.put("error", "need location"); return j; }
            List<CellInfo> cells = tm.getAllCellInfo();
            if (cells == null) { j.put("count", 0); return j; }
            JSONArray arr = new JSONArray();
            for (CellInfo ci : cells) {
                if (ci == null || !ci.isRegistered()) continue;
                JSONObject c = cell2json(ci);
                if (c.length() > 1) arr.put(c);
            }
            j.put("count", arr.length());
            j.put("list", arr);
        } catch (Exception e) {}
        return j;
    }

    public JSONObject getQosInfo() {
        JSONObject j = new JSONObject();
        try {
            j.put("qci", lastQci);
            if (Build.VERSION.SDK_INT >= 30) {
                Network n = cm.getActiveNetwork();
                if (n != null) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                    if (nc != null) {
                        j.put("downKbps", nc.getLinkDownstreamBandwidthKbps());
                        j.put("upKbps", nc.getLinkUpstreamBandwidthKbps());
                    }
                }
            }
        } catch (Exception e) {}
        return j;
    }

    public JSONObject getHiddenServiceState() {
        JSONObject j = new JSONObject();
        try {
            java.lang.reflect.Method m = TelephonyManager.class.getMethod("getServiceState");
            Object ss = m.invoke(tm);
            j.put("raw", ss != null ? ss.toString() : "null");
        } catch (Exception e) { j.put("error", e.getMessage()); }
        return j;
    }

    public JSONObject getNativeDecoderStatus() {
        JSONObject j = new JSONObject();
        try {
            j.put("lteNas", ck("tphone.airmessage.decode.nas.NasDecoder"));
            j.put("nrNas", ck("tphone.airmessage.decode.nrnas.NrnasDecoder"));
            j.put("lteRrc", ck("tphone.airmessage.decode.LteRrcDecoder"));
            j.put("nrRrc", ck("tphone.airmessage.decode.Nr5gRrcDecoder"));
            j.put("note", "need root + /dev/diag");
        } catch (Exception e) {}
        return j;
    }

    public String decodeRawMessage(byte[] raw, int rat) {
        try {
            switch (rat) {
                case 0: if (ck("tphone.airmessage.decode.nas.NasDecoder"))
                    return tphone.airmessage.decode.nas.NasDecoder.getNASMsgParseDetails(raw, raw.length); break;
                case 1: if (ck("tphone.airmessage.decode.nrnas.NrnasDecoder"))
                    return tphone.airmessage.decode.nrnas.NrnasDecoder.getNASMsgParseDetails(raw, raw.length); break;
            }
        } catch (Exception e) {}
        return null;
    }

    public JSONObject getAllInfo() {
        JSONObject j = new JSONObject();
        try {
            j.put("sim", getSimInfo());
            j.put("network", getNetworkTypeInfo());
            j.put("signal", getSignalInfo());
            j.put("cells", getCellInfo());
            j.put("qos", getQosInfo());
            j.put("hidden", getHiddenServiceState());
            j.put("decoders", getNativeDecoderStatus());
            j.put("ts", System.currentTimeMillis());
            j.put("sdk", Build.VERSION.SDK_INT);
        } catch (Exception e) {}
        return j;
    }

    public void registerQciListener() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            java.lang.reflect.Method reg = TelephonyManager.class.getMethod("registerQosCallback",
                    java.util.concurrent.Executor.class, Class.forName("android.telephony.QosCallback"));
            Object cb = java.lang.reflect.Proxy.newProxyInstance(
                    Class.forName("android.telephony.QosCallback").getClassLoader(),
                    new Class[]{Class.forName("android.telephony.QosCallback")},
                    (p, m, a) -> {
                        if ("onQosSessionsChanged".equals(m.getName()) && a != null && a.length > 0) {
                            for (Object s : (List<?>) a[0]) {
                                try {
                                    if ((int) s.getClass().getMethod("getType").invoke(s) == 3) {
                                        Object attrs = s.getClass().getMethod("getEpsBearerQosSessionAttributes").invoke(s);
                                        if (attrs != null) {
                                            int qci = (int) attrs.getClass().getMethod("getQci").invoke(attrs);
                                            if (qci != lastQci) { lastQci = qci; Log.i(TAG, "QCI:" + qci); }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                        return null;
                    });
            reg.invoke(tm, Executors.newSingleThreadExecutor(), cb);
            Log.i(TAG, "QCI listener OK");
        } catch (Exception e) { Log.w(TAG, "QCI failed: " + e.getMessage()); }
    }

    @SuppressLint("MissingPermission")
    private JSONObject cell2json(CellInfo ci) {
        JSONObject c = new JSONObject();
        try {
            if (ci instanceof CellInfoLte) {
                CellInfoLte lte = (CellInfoLte) ci;
                CellSignalStrengthLte sig = lte.getCellSignalStrength();
                if (sig == null) return c;
                c.put("type", "LTE"); c.put("tac", lte.getCellIdentity().getTac());
                c.put("ci", lte.getCellIdentity().getCi()); c.put("pci", lte.getCellIdentity().getPci());
                c.put("rsrp", sig.getRsrp()); c.put("dbm", sig.getDbm());
            } else if (ci instanceof CellInfoNr) {
                CellInfoNr nr = (CellInfoNr) ci;
                CellSignalStrengthNr sig = (CellSignalStrengthNr) nr.getCellSignalStrength();
                if (sig == null) return c;
                c.put("type", "NR");
                if (nr.getCellIdentity() instanceof CellIdentityNr) {
                    CellIdentityNr id = (CellIdentityNr) nr.getCellIdentity();
                    c.put("tac", id.getTac()); c.put("nci", id.getNci()); c.put("pci", id.getPci());
                }
                c.put("rsrp", sig.getSsRsrp()); c.put("rsrq", sig.getSsRsrq());
                c.put("sinr", sig.getSsSinr()); c.put("dbm", sig.getDbm());
            }
        } catch (Exception ignored) {}
        return c;
    }

    private boolean hasP() { return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED; }
    private boolean hasL() { return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    @SuppressLint("MissingPermission")
    private String imei(int slot) { try { return hasP() ? tm.getImei(slot) : null; } catch (Exception e) { return null; } }

    private boolean ck(String cn) { try { Class.forName(cn).getMethod("isAvailable").invoke(null); return true; } catch (Exception e) { return false; } }
    private String s(String v) { return v == null ? "" : v; }
    private String simSt(int st) { return st == 5 ? "READY" : "?" + st; }
    private String nn(int t) { return t == 20 ? "NR" : t == 19 ? "LTE" : t == 3 ? "UMTS" : t == 16 ? "GSM" : "?" + t; }
}
