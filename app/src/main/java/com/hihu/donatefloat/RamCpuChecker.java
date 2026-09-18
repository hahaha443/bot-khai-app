package com.hihu.donatefloat;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

/**
 * Đọc RAM/CPU của các app khác và cho phép buộc dừng, thông qua Shizuku
 * (không cần root thật). Người dùng cần tự cài app Shizuku và kích hoạt
 * 1 lần qua ADB / Wireless debugging. Shizuku chạy lệnh với quyền UID
 * "shell" (giống adb shell), nên đọc/điều khiển được app khác mà app
 * thường không làm được kể từ Android 7+.
 */
public class RamCpuChecker {

    /** 1 dòng kết quả gộp cả RAM + CPU của 1 app. */
    public static class AppUsage {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public final long pssKb;
        public final float cpuPercent;
        public final boolean isSystemApp;      // dùng để cảnh báo trước khi buộc dừng
        public final boolean hasLauncherIcon;   // dùng để chia tab "người dùng" / "hệ thống"

        public AppUsage(String packageName, String label, Drawable icon,
                         long pssKb, float cpuPercent, boolean isSystemApp, boolean hasLauncherIcon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.pssKb = pssKb;
            this.cpuPercent = cpuPercent;
            this.isSystemApp = isSystemApp;
            this.hasLauncherIcon = hasLauncherIcon;
        }
    }

    /** Quy đổi KB sang KB/MB/GB/TB cho dễ đọc. */
    public static String formatSize(long kb) {
        double v = kb;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(java.util.Locale.getDefault(), "%.2f %s", v, units[i]);
    }

    public enum SortBy { RAM, CPU }

    private static final Pattern MEMINFO_HEADER =
            Pattern.compile("\\*\\* MEMINFO in pid (\\d+) \\[([\\w.:]+)] \\*\\*");

    /** Có quyền Shizuku đầy đủ chưa (đã cài + đang chạy + user đã cấp quyền). */
    public static boolean isReady() {
        try {
            return Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shizuku đã cài & service đang chạy chưa (chưa chắc đã cấp quyền). */
    public static boolean isShizukuInstalledAndRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Shizuku app đã cài trên máy chưa (dù service có chạy hay không). */
    public static boolean isShizukuAppInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(requestCode);
        }
    }

    private static IUserService userService;
    private static final Object BIND_LOCK = new Object();

    private static IUserService ensureUserService() throws Exception {
        synchronized (BIND_LOCK) {
            if (userService != null) return userService;

            final CountDownLatch latch = new CountDownLatch(1);
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("ramcpu")
                    .debuggable(BuildConfig.DEBUG)
                    .version(1);

            ServiceConnection connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder binder) {
                    userService = IUserService.Stub.asInterface(binder);
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    userService = null;
                }
            };

            Shizuku.bindUserService(args, connection);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new Exception("Kết nối UserService quá thời gian chờ (timeout)");
            }
            if (userService == null) {
                throw new Exception("Không kết nối được UserService");
            }
            return userService;
        }
    }

    private static String runCommand(String... cmd) throws Exception {
        String result = ensureUserService().exec(cmd);
        if (result != null && result.startsWith("__ERROR__:")) {
            throw new Exception(result.substring("__ERROR__:".length()));
        }
        return result;
    }

    /** Danh sách gộp RAM+CPU từng app, kèm tên/icon thật, sắp theo sortBy. */
    public static List<AppUsage> getUsageList(Context ctx, int limit, SortBy sortBy) throws Exception {
        Map<String, Long> ramByPkg = new HashMap<>();
        Map<Integer, String> pidToPkg = new HashMap<>();

        String meminfoRaw = runCommand("sh", "-c", "dumpsys meminfo -a");
        String currentPkg = null;
        for (String line : meminfoRaw.split("\n")) {
            Matcher header = MEMINFO_HEADER.matcher(line);
            if (header.find()) {
                currentPkg = header.group(2);
                pidToPkg.put(Integer.parseInt(header.group(1)), currentPkg);
                continue;
            }
            if (currentPkg != null && line.trim().startsWith("TOTAL PSS:")) {
                Matcher num = Pattern.compile("\\d+").matcher(line.trim());
                if (num.find()) {
                    long pss = Long.parseLong(num.group());
                    ramByPkg.merge(currentPkg, pss, Long::sum);
                }
                currentPkg = null;
            }
        }

        Map<String, Float> cpuByPkg = new HashMap<>();
        String topRaw = runCommand("sh", "-c", "top -b -n 1 -o %CPU,ARGS");
        for (String line : topRaw.split("\n")) {
            String[] cols = line.trim().split("\\s+");
            if (cols.length < 2) continue;
            Integer pid = tryParseInt(cols[0]);
            if (pid == null) continue;
            Float cpu = null;
            for (int i = cols.length - 1; i >= 0; i--) {
                Float f = tryParseFloat(cols[i]);
                if (f != null) { cpu = f; break; }
            }
            String pkg = pidToPkg.get(pid);
            if (cpu != null && pkg != null) {
                cpuByPkg.merge(pkg, cpu, Float::sum);
            }
        }

        PackageManager pm = ctx.getPackageManager();
        Map<String, AppUsage> merged = new HashMap<>();
        java.util.Set<String> allPkgs = new java.util.HashSet<>();
        allPkgs.addAll(ramByPkg.keySet());
        allPkgs.addAll(cpuByPkg.keySet());

        for (String pkg : allPkgs) {
            String label = pkg;
            Drawable icon = null;
            boolean isSystem = true;
            boolean hasLauncher = false;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = pm.getApplicationLabel(ai).toString();
                icon = pm.getApplicationIcon(ai);
                isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                hasLauncher = pm.getLaunchIntentForPackage(pkg) != null;
            } catch (PackageManager.NameNotFoundException ignored) {
                // Process hệ thống không phải 1 package cài đặt thật (vd: system, zygote...)
                // hoặc là process con dạng "package:tên_process" — thử tách phần trước ":"
                // để vẫn lấy được tên/icon của app gốc.
                int colon = pkg.indexOf(':');
                if (colon > 0) {
                    String basePkg = pkg.substring(0, colon);
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(basePkg, 0);
                        label = pm.getApplicationLabel(ai).toString() + " (" + pkg.substring(colon + 1) + ")";
                        icon = pm.getApplicationIcon(ai);
                        isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                        hasLauncher = pm.getLaunchIntentForPackage(basePkg) != null;
                    } catch (PackageManager.NameNotFoundException ignored2) {
                    }
                }
            }
            long pss = ramByPkg.getOrDefault(pkg, 0L);
            float cpu = cpuByPkg.getOrDefault(pkg, 0f);
            merged.put(pkg, new AppUsage(pkg, label, icon, pss, cpu, isSystem, hasLauncher));
        }

        List<AppUsage> result = new ArrayList<>(merged.values());
        if (sortBy == SortBy.CPU) {
            result.sort((a, b) -> Float.compare(b.cpuPercent, a.cpuPercent));
        } else {
            result.sort((a, b) -> Long.compare(b.pssKb, a.pssKb));
        }
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /** Buộc dừng 1 app (giống "Force stop" trong Cài đặt hệ thống). */
    public static void forceStopApp(String packageName) throws Exception {
        runCommand("sh", "-c", "am force-stop " + packageName);
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static Float tryParseFloat(String s) {
        try { return Float.parseFloat(s); } catch (Exception e) { return null; }
    }
}
