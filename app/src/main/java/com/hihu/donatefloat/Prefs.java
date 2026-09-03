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

    public static boolean panelLocked(Context ctx, String key) {
        return sp(ctx).getBoolean("locked_" + key, false);
    }

    public static void setPanelLocked(Context ctx, String key, boolean v) {
        sp(ctx).edit().putBoolean("locked_" + key, v).apply();
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

    // ─── Menu Ghi chú nổi (menu rời #3, hỗ trợ NHIỀU ghi chú cùng lúc) ───
    public static String notesListRaw(Context ctx) {
        return sp(ctx).getString("notes_list", "[]");
    }

    public static void setNotesListRaw(Context ctx, String jsonArray) {
        sp(ctx).edit().putString("notes_list", jsonArray).apply();
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

    // ─── Màu chữ chung (áp cho Báo cáo, Ghi chú, thanh đo mục tiêu) ───
    public static int textColor(Context ctx) {
        return sp(ctx).getInt("text_color", 0xFFFFFFFF);
    }

    public static void setTextColor(Context ctx, int v) {
        sp(ctx).edit().putInt("text_color", v).apply();
    }

    // ─── Menu #4: thanh đo mục tiêu donate ───
    public static String goalTitle(Context ctx) {
        return sp(ctx).getString("goal_title", "Mục tiêu donate hôm nay");
    }

    public static void setGoalTitle(Context ctx, String v) {
        sp(ctx).edit().putString("goal_title", v).apply();
    }

    public static long goalAmount(Context ctx) {
        return sp(ctx).getLong("goal_amount", 5_000_000L);
    }

    public static void setGoalAmount(Context ctx, long v) {
        sp(ctx).edit().putLong("goal_amount", v).apply();
    }

    public static int goalSizeDp(Context ctx) {
        return sp(ctx).getInt("goal_size_dp", 300);
    }

    public static void setGoalSizeDp(Context ctx, int v) {
        sp(ctx).edit().putInt("goal_size_dp", v).apply();
    }

    public static int goalHeightDp(Context ctx) {
        return sp(ctx).getInt("goal_height_dp", 110);
    }

    public static void setGoalHeightDp(Context ctx, int v) {
        sp(ctx).edit().putInt("goal_height_dp", v).apply();
    }

    public static int goalOpacity(Context ctx) {
        return sp(ctx).getInt("goal_opacity", 90);
    }

    public static void setGoalOpacity(Context ctx, int v) {
        sp(ctx).edit().putInt("goal_opacity", v).apply();
    }
}
