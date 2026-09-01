package com.hihu.donatefloat;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private static final String NAME = "donate_float_prefs";

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String serverUrl(Context ctx) {
        return sp(ctx).getString("server_url", "http://100.0.0.1:8000");
    }

    public static void setServerUrl(Context ctx, String v) {
        sp(ctx).edit().putString("server_url", v).apply();
    }

    public static String apiToken(Context ctx) {
        return sp(ctx).getString("api_token", "");
    }

    public static void setApiToken(Context ctx, String v) {
        sp(ctx).edit().putString("api_token", v).apply();
    }

    public static int reportSizeDp(Context ctx) {
        return sp(ctx).getInt("report_size_dp", 280);
    }

    public static void setReportSizeDp(Context ctx, int v) {
        sp(ctx).edit().putInt("report_size_dp", v).apply();
    }

    public static int qrSizeDp(Context ctx) {
        return sp(ctx).getInt("qr_size_dp", 240);
    }

    public static void setQrSizeDp(Context ctx, int v) {
        sp(ctx).edit().putInt("qr_size_dp", v).apply();
    }

    public static int lastSeenId(Context ctx) {
        return sp(ctx).getInt("last_seen_id", 0);
    }

    public static void setLastSeenId(Context ctx, int v) {
        sp(ctx).edit().putInt("last_seen_id", v).apply();
    }
}
