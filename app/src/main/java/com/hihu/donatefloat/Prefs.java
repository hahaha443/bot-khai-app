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

    // Độ mờ tính theo %, 100 = hiện rõ hoàn toàn, càng thấp càng trong suốt
    public static int reportOpacity(Context ctx) {
        return sp(ctx).getInt("report_opacity", 90);
    }

    public static void setReportOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("report_opacity", v).apply();
    }

    public static int qrOpacity(Context ctx) {
        return sp(ctx).getInt("qr_opacity", 90);
    }

    public static void setQrOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("qr_opacity", v).apply();
    }

    public static int alertOpacity(Context ctx) {
        return sp(ctx).getInt("alert_opacity", 95);
    }

    public static void setAlertOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("alert_opacity", v).apply();
    }

    public static boolean autoStartMenus(Context ctx) {
        return sp(ctx).getBoolean("auto_start_menus", false);
    }

    public static void setAutoStartMenus(Context ctx, boolean v) {
        sp(ctx).edit().putBoolean("auto_start_menus", v).apply();
    }
}
