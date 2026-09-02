package com.hihu.donatefloat;

import android.content.Context;

public final class LockManager {
    private LockManager() {}

    public static void setLocked(Context ctx, boolean locked) {
        Prefs.setLocked(ctx, locked);
        FloatingReportService.updateLocked(ctx);
        FloatingQRService.updateLocked(ctx);
        FloatingNoteService.updateLocked(ctx);
        FloatingGoalService.updateLocked(ctx);
        LockBubble.refresh(ctx);
    }

    public static void toggle(Context ctx) {
        setLocked(ctx, !Prefs.locked(ctx));
    }
}
