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

    public static int reportHeightDp(Context ctx) {
        return sp(ctx).getInt("report_height_dp", 220);
    }

    public static void setReportHeightDp(Context ctx, int v) {
        sp(ctx).edit().putInt("report_height_dp", v).apply();
    }

    public static int qrSizeDp(Context ctx) {
        return sp(ctx).getInt("qr_size_dp", 240);
    }

    public static void setQrSizeDp(Context ctx, int v) {
        sp(ctx).edit().putInt("qr_size_dp", v).apply();
    }

    public static int qrHeightDp(Context ctx) {
        return sp(ctx).getInt("qr_height_dp", 300);
    }

    public static void setQrHeightDp(Context ctx, int v) {
        sp(ctx).edit().putInt("qr_height_dp", v).apply();
    }

    public static boolean locked(Context ctx) {
        return sp(ctx).getBoolean("locked", false);
    }

    public static void setLocked(Context ctx, boolean v) {
        sp(ctx).edit().putBoolean("locked", v).apply();
    }

    public static int lastSeenId(Context ctx) {
        return sp(ctx).getInt("last_seen_id", 0);
    }

    public static void setLastSeenId(Context ctx, int v) {
        sp(ctx).edit().putInt("last_seen_id", v).apply();
    }

    // Độ mờ tính theo %, 100 = hiện rõ hoàn toàn, 0 = trong suốt hẳn
    // Tách riêng: mờ NỀN (khung/background) và mờ NỘI DUNG (chữ) của bảng Báo cáo
    public static int reportBgOpacity(Context ctx) {
        return sp(ctx).getInt("report_bg_opacity", 90);
    }

    public static void setReportBgOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("report_bg_opacity", v).apply();
    }

    public static int reportContentOpacity(Context ctx) {
        return sp(ctx).getInt("report_content_opacity", 100);
    }

    public static void setReportContentOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("report_content_opacity", v).apply();
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

    // ─── Menu Ghi chú nổi (menu rời thứ 3) ───
    public static String noteText(Context ctx) {
        return sp(ctx).getString("note_text", "Mục tiêu donate hôm nay: 500k 🎯");
    }

    public static void setNoteText(Context ctx, String v) {
        sp(ctx).edit().putString("note_text", v).apply();
    }

    public static int noteSizeDp(Context ctx) {
        return sp(ctx).getInt("note_size_dp", 220);
    }

    public static void setNoteSizeDp(Context ctx, int v) {
        sp(ctx).edit().putInt("note_size_dp", v).apply();
    }

    public static int noteHeightDp(Context ctx) {
        return sp(ctx).getInt("note_height_dp", 90);
    }

    public static void setNoteHeightDp(Context ctx, int v) {
        sp(ctx).edit().putInt("note_height_dp", v).apply();
    }

    public static int noteOpacity(Context ctx) {
        return sp(ctx).getInt("note_opacity", 90);
    }

    public static void setNoteOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("note_opacity", v).apply();
    }
}
