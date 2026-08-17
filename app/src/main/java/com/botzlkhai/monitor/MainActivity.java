package com.botzlkhai.monitor;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Đổi đúng domain/host thật của mày
    private static final String API_BASE = "http://zrmteam.x10.mx/app-bot-zeplo/api";
    // Giống bản web: khi không có avatar thật, dùng ảnh đại diện tự sinh
    // (identicon) theo uid thay vì để trống xám — nhóm/bạn bè không có avatar
    // trên Zalo là chuyện bình thường, không phải lỗi, chỉ cần hiện gì đó thay thế.
    private static final String AVATAR_FALLBACK = "https://api.dicebear.com/7.x/identicon/png?seed=";

    // Màu KHÔNG còn static final — đổi được lúc chạy khi bật/tắt theme sáng/tối
    // (xem applyThemeColors()). Đổi theme xong app tự recreate() để build lại
    // toàn bộ UI với màu mới, khỏi phải dò từng View đã tạo để cập nhật tay.
    private int BG, CARD, ACC, ACC_TEXT, OK, BAD, TXT, SUB, DIVIDER;
    private static final String NOT_LINKED_MSG =
            "🔒 Chưa liên kết token quản lý bot — vào tab Token, dán token rồi bấm Liên kết để xem được mục này.";

    private boolean darkMode = true;

    private void applyThemeColors() {
        if (darkMode) {
            BG = Color.parseColor("#0a0d12");
            CARD = Color.parseColor("#12161d");
            ACC = Color.parseColor("#f5a544");
            ACC_TEXT = Color.parseColor("#1a1206"); // chữ tối trên nền amber sáng — đủ tương phản
            OK = Color.parseColor("#22c08f");
            BAD = Color.parseColor("#f0594a");
            TXT = Color.parseColor("#eef0f0");
            SUB = Color.parseColor("#7b8494");
            DIVIDER = Color.parseColor("#1c222c");
        } else {
            BG = Color.parseColor("#f6f5f1");
            CARD = Color.parseColor("#ffffff");
            ACC = Color.parseColor("#c97a2e");
            ACC_TEXT = Color.parseColor("#ffffff");
            OK = Color.parseColor("#0f9d70");
            BAD = Color.parseColor("#d1483a");
            TXT = Color.parseColor("#1b1f27");
            SUB = Color.parseColor("#6b7280");
            DIVIDER = Color.parseColor("#e6e3d9");
        }
    }

    private android.graphics.drawable.Drawable roundedBg(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return d;
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        prefs.edit().putBoolean("dark_mode", darkMode).apply();
        recreate(); // build lại toàn bộ UI với màu mới — đơn giản, chắc ăn, không sót View nào
    }

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String sessionId;
    private String currentRole = "member";

    private FrameLayout root;
    private LinearLayout loginView, mainView;
    private TextView statusDot, statusText, uptimeVal, sysVal, groupsVal, friendsVal, botTitleView;
    private TextView pingVal, apiLatencyVal, softwareVal, softwareVerVal, startedAtVal;
    private TextView accTypeVal, uptime24Val, uptime7dVal;
    private long lastUptimeStatsCallMs = 0; // throttle riêng — refreshStatus() chạy mỗi 500ms nhưng uptime_stats không cần gọi dày vậy
    private LinearLayout onlineStatusView;
    private LinearLayout contentArea;
    private JSONObject groupsCache = new JSONObject(), friendsCache = new JSONObject();
    private String currentGroupId, currentFriendId;
    private boolean polling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bot_khai_prefs", MODE_PRIVATE);
        sessionId = prefs.getString("session_id", null);
        darkMode = prefs.getBoolean("dark_mode", true);
        applyThemeColors();

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        getWindow().setStatusBarColor(BG);
        // Theme sáng -> nền status bar trắng, icon (đồng hồ/wifi/pin) mặc định
        // màu trắng sẽ bị chìm mất -> báo hệ thống đổi icon sang màu đen.
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (!darkMode) {
            getWindow().getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        if (sessionId != null) showMain(); else showLogin();
    }

    // ================= LOGIN =================
    private void showLogin() {
        root.removeAllViews();
        loginView = new LinearLayout(this);
        loginView.setOrientation(LinearLayout.VERTICAL);
        loginView.setGravity(Gravity.CENTER);
        loginView.setBackgroundColor(BG);
        loginView.setPadding(48, 48, 48, 48);

        // Logo vẽ bằng code (không dùng emoji robot nữa): ô vuông bo góc màu
        // amber + hình thoi nhỏ ở giữa — đồng bộ chấm trạng thái pulse trên header.
        FrameLayout logo = new FrameLayout(this);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(140, 140);
        logoLp.bottomMargin = 28;
        logo.setLayoutParams(logoLp);
        logo.setBackground(roundedBg(ACC, 32));
        View diamond = new View(this);
        FrameLayout.LayoutParams dLp2 = new FrameLayout.LayoutParams(40, 40);
        dLp2.gravity = Gravity.CENTER;
        diamond.setLayoutParams(dLp2);
        diamond.setBackgroundColor(BG);
        diamond.setRotation(45f);
        logo.addView(diamond);
        loginView.addView(logo);

        TextView title = new TextView(this);
        title.setText("Bot-ZL-Khai Monitor");
        title.setTextColor(TXT);
        title.setTextSize(19);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        loginView.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Đăng nhập để tiếp tục");
        subtitle.setTextColor(SUB);
        subtitle.setTextSize(12);
        subtitle.setPadding(0, 6, 0, 28);
        loginView.addView(subtitle);

        TextView err = new TextView(this);
        err.setTextColor(BAD);
        err.setTextSize(13);
        err.setPadding(0, 0, 0, 12);
        loginView.addView(err);

        LinearLayout loginBox = new LinearLayout(this);
        loginBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout registerBox = new LinearLayout(this);
        registerBox.setOrientation(LinearLayout.VERTICAL);
        registerBox.setVisibility(View.GONE);

        EditText user = new EditText(this);
        user.setHint("Tài khoản");
        styleInput(user);
        loginBox.addView(user);

        EditText pass = new EditText(this);
        pass.setHint("Mật khẩu");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(pass);
        loginBox.addView(pass);

        Button loginBtn = new Button(this);
        loginBtn.setText("Đăng nhập");
        loginBtn.setTextColor(ACC_TEXT);
        loginBtn.setBackgroundColor(ACC);
        loginBtn.setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            if (u.isEmpty() || p.isEmpty()) { err.setTextColor(BAD); err.setText("Nhập đủ tài khoản và mật khẩu."); return; }
            // Phản hồi ngay lập tức khi bấm — trước đây bấm xong không thấy gì
            // đổi cho tới khi request xong (hoặc treo), nhìn như nút không hoạt động.
            err.setTextColor(SUB);
            err.setText("Đang đăng nhập...");
            loginBtn.setEnabled(false);
            doLogin(u, p, err, loginBtn);
        });
        applyPressFeedback(loginBtn);
        loginBox.addView(loginBtn);

        TextView toRegister = authSwitchLink("Chưa có tài khoản? Đăng ký", () -> {
            err.setText(""); subtitle.setText("Cần mã mời từ admin để đăng ký");
            loginBox.setVisibility(View.GONE); registerBox.setVisibility(View.VISIBLE);
        });
        loginBox.addView(toRegister);

        EditText regUser = new EditText(this);
        regUser.setHint("Tài khoản mới (chữ/số/_ , tối thiểu 3 ký tự)");
        styleInput(regUser);
        registerBox.addView(regUser);

        EditText regPass = new EditText(this);
        regPass.setHint("Mật khẩu (tối thiểu 6 ký tự)");
        regPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(regPass);
        registerBox.addView(regPass);

        EditText regInvite = new EditText(this);
        regInvite.setHint("Mã mời (xin admin cấp)");
        styleInput(regInvite);
        registerBox.addView(regInvite);

        Button registerBtn = new Button(this);
        registerBtn.setText("Đăng ký");
        registerBtn.setTextColor(ACC_TEXT);
        registerBtn.setBackgroundColor(ACC);
        registerBtn.setOnClickListener(v -> {
            String u = regUser.getText().toString().trim();
            String p = regPass.getText().toString();
            String inv = regInvite.getText().toString().trim();
            if (u.isEmpty() || p.isEmpty() || inv.isEmpty()) { err.setTextColor(BAD); err.setText("Điền đủ tài khoản, mật khẩu và mã mời."); return; }
            err.setTextColor(SUB);
            err.setText("Đang đăng ký...");
            registerBtn.setEnabled(false);
            doRegister(u, p, inv, err, registerBtn);
        });
        applyPressFeedback(registerBtn);
        registerBox.addView(registerBtn);

        TextView toLogin = authSwitchLink("Đã có tài khoản? Đăng nhập", () -> {
            err.setText(""); subtitle.setText("Đăng nhập để tiếp tục");
            registerBox.setVisibility(View.GONE); loginBox.setVisibility(View.VISIBLE);
        });
        registerBox.addView(toLogin);

        loginView.addView(loginBox);
        loginView.addView(registerBox);
        root.addView(loginView);
    }

    private TextView authSwitchLink(String text, Runnable onClick) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(ACC);
        t.setTextSize(12.5f);
        t.setPadding(0, 16, 0, 0);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    private void styleInput(EditText e) {
        e.setTextColor(TXT);
        e.setHintTextColor(SUB);
        e.setBackground(roundedBg(CARD, 10));
        e.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 20;
        e.setLayoutParams(lp);
    }

    private void fetchWhoAmI() {
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/whoami.php", null, sessionId);
            if (res != null) currentRole = res.optString("role", "member");
        });
    }

    private void doLogin(String username, String password, TextView err, Button btn) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "login");
                body.put("username", username);
                body.put("password", password);
                JSONObject res = httpJson("POST", "/auth.php", body, null);
                if (res != null && res.optBoolean("ok", false)) {
                    sessionId = res.getString("session_id");
                    currentRole = res.optString("role", "member");
                    prefs.edit().putString("session_id", sessionId).apply();
                    ui.post(this::showMain);
                } else {
                    String msg = res != null ? res.optString("message", "Sai tài khoản hoặc mật khẩu.") : "Sai tài khoản hoặc mật khẩu.";
                    ui.post(() -> { btn.setEnabled(true); err.setTextColor(BAD); err.setText(msg); });
                }
            } catch (Exception e) {
                ui.post(() -> { btn.setEnabled(true); err.setTextColor(BAD); err.setText("Lỗi kết nối server."); });
            }
        });
    }

    private void doRegister(String username, String password, String invite, TextView err, Button btn) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "register");
                body.put("username", username);
                body.put("password", password);
                body.put("invite_code", invite);
                JSONObject res = httpJson("POST", "/auth.php", body, null);
                if (res != null && res.optBoolean("ok", false)) {
                    sessionId = res.getString("session_id");
                    currentRole = res.optString("role", "member");
                    prefs.edit().putString("session_id", sessionId).apply();
                    ui.post(this::showMain);
                } else {
                    String msg = res != null ? res.optString("message", "Đăng ký thất bại.") : "Đăng ký thất bại.";
                    ui.post(() -> { btn.setEnabled(true); err.setTextColor(BAD); err.setText(msg); });
                }
            } catch (Exception e) {
                ui.post(() -> { btn.setEnabled(true); err.setTextColor(BAD); err.setText("Lỗi kết nối server."); });
            }
        });
    }

    private void logout() {
        polling = false;
        sessionId = null;
        prefs.edit().remove("session_id").apply();
        showLogin();
    }

    // ================= MAIN APP =================
    private void showMain() {
        root.removeAllViews();
        mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        mainView.setBackgroundColor(BG);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(32, 32, 32, 16);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout logo = new FrameLayout(this);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(66, 66);
        logoLp.rightMargin = 20;
        logo.setLayoutParams(logoLp);
        logo.setBackground(roundedBg(ACC, 18));
        View diamond = new View(this);
        FrameLayout.LayoutParams dLp3 = new FrameLayout.LayoutParams(20, 20);
        dLp3.gravity = Gravity.CENTER;
        diamond.setLayoutParams(dLp3);
        diamond.setBackgroundColor(BG);
        diamond.setRotation(45f);
        logo.addView(diamond);
        header.addView(logo);

        TextView title = new TextView(this);
        title.setText("Bot-ZL-Khai");
        title.setTextColor(TXT);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        botTitleView = title;
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        header.addView(title, titleLp);
        statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextColor(BAD);
        header.addView(statusDot);
        statusText = new TextView(this);
        statusText.setText(" Đang kiểm tra...");
        statusText.setTextColor(TXT);
        header.addView(statusText);
        fetchWhoAmI();

        Button themeBtn = new Button(this);
        themeBtn.setText(darkMode ? "🌙" : "☀️");
        themeBtn.setTextColor(TXT);
        themeBtn.setBackground(roundedBg(CARD, 20));
        themeBtn.setPadding(20, 8, 20, 8);
        themeBtn.setMinWidth(0);
        themeBtn.setMinHeight(0);
        themeBtn.setElevation(0);
        LinearLayout.LayoutParams themeBtnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        themeBtnLp.leftMargin = 16;
        themeBtn.setLayoutParams(themeBtnLp);
        themeBtn.setOnClickListener(v -> toggleTheme());
        header.addView(themeBtn);

        mainView.addView(header);

        // Content area (scrollable)
        ScrollView scroll = new ScrollView(this);
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(contentArea);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        mainView.addView(scroll, scrollLp);

        // Bottom nav
        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(CARD);
        String[] tabs = {"📊 Dashboard","👥 Nhóm","🧑‍🤝‍🧑 Bạn bè","⚙️ Lệnh","🎮 Điều khiển","🔑 Token","📝 Log"};
        String[] keys = {"dashboard","groups","friends","commands","control","settings","log"};
        for (int i = 0; i < tabs.length; i++) {
            Button b = new Button(this);
            b.setText(tabs[i]);
            b.setTextSize(11);
            b.setTextColor(SUB);
            b.setBackground(roundedBg(CARD, 12));
            String key = keys[i];
            b.setOnClickListener(v -> switchTab(key));
            nav.addView(b, new LinearLayout.LayoutParams(220, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        navScroll.addView(nav);
        mainView.addView(navScroll);

        root.addView(mainView);

        switchTab("dashboard");
        startPolling();
    }

    private void startPolling() {
        if (polling) return;
        polling = true;
        Runnable loop = new Runnable() {
            @Override public void run() {
                if (!polling) return;
                refreshStatus();
                ui.postDelayed(this, 500);
            }
        };
        ui.post(loop);
    }

    // ================= TAB SWITCH =================
    private void switchTab(String tab) {
        contentArea.removeAllViews();
        switch (tab) {
            case "dashboard": buildDashboardTab(); break;
            case "groups": buildGroupsTab(); break;
            case "friends": buildFriendsTab(); break;
            case "commands": buildCommandsTab(); break;
            case "control": buildControlTab(); break;
            case "settings": buildSettingsTab(); break;
            case "log": buildLogTab(); break;
        }
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(SUB);
        t.setTextSize(13);
        t.setPadding(32, 24, 32, 12);
        return t;
    }

    private LinearLayout card(String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(roundedBg(CARD, 14));
        c.setPadding(28, 28, 28, 28);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(8, 8, 8, 8);
        c.setLayoutParams(lp);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(SUB);
        lab.setTextSize(12);
        c.addView(lab);
        return c;
    }

    // ---------- Dashboard ----------
    private void buildDashboardTab() {
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(24, 16, 24, 0);
        LinearLayout c1 = card("Uptime");
        uptimeVal = valText("-"); c1.addView(uptimeVal);
        LinearLayout c2 = card("Khởi động lúc");
        startedAtVal = valText("-"); startedAtVal.setTextSize(15);
        c2.addView(startedAtVal);
        row1.addView(c1); row1.addView(c2);
        contentArea.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(24, 0, 24, 0);
        LinearLayout c3 = card("Số nhóm");
        groupsVal = valText("-"); c3.addView(groupsVal);
        c3.setOnClickListener(v -> switchTab("groups"));
        LinearLayout c4 = card("Bạn bè");
        friendsVal = valText("-"); c4.addView(friendsVal);
        c4.setOnClickListener(v -> switchTab("friends"));
        row2.addView(c3); row2.addView(c4);
        contentArea.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(24, 0, 24, 0);
        LinearLayout c5 = card("Ping mạng");
        pingVal = valText("-"); c5.addView(pingVal);
        LinearLayout c6 = card("Độ trễ API (máy chủ)");
        apiLatencyVal = valText("-"); apiLatencyVal.setTextSize(18);
        c6.addView(apiLatencyVal);
        row3.addView(c5); row3.addView(c6);
        contentArea.addView(row3);

        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.setPadding(24, 0, 24, 0);
        LinearLayout c7 = card("Phần mềm");
        softwareVal = valText("-"); softwareVal.setTextSize(18);
        c7.addView(softwareVal);
        LinearLayout c8 = card("Phiên bản");
        softwareVerVal = valText("-"); softwareVerVal.setTextSize(18);
        c8.addView(softwareVerVal);
        row4.addView(c7); row4.addView(c8);
        contentArea.addView(row4);

        LinearLayout row5 = new LinearLayout(this);
        row5.setOrientation(LinearLayout.HORIZONTAL);
        row5.setPadding(24, 0, 24, 0);
        LinearLayout c9 = card("Nhiệt độ / RAM");
        sysVal = valText("-"); c9.addView(sysVal);
        row5.addView(c9);
        contentArea.addView(row5);

        LinearLayout row6 = new LinearLayout(this);
        row6.setOrientation(LinearLayout.HORIZONTAL);
        row6.setPadding(24, 0, 24, 0);
        LinearLayout c10 = card("Tài khoản Zalo");
        accTypeVal = valText("-"); c10.addView(accTypeVal);
        LinearLayout uptimeRow = new LinearLayout(this);
        uptimeRow.setOrientation(LinearLayout.HORIZONTAL);
        uptimeRow.setPadding(0, 12, 0, 0);
        LinearLayout u24box = new LinearLayout(this);
        u24box.setOrientation(LinearLayout.VERTICAL);
        u24box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView u24lab = new TextView(this);
        u24lab.setText("Uptime 24h"); u24lab.setTextColor(SUB); u24lab.setTextSize(11);
        uptime24Val = valText("—"); uptime24Val.setTextSize(16); uptime24Val.setPadding(0, 2, 0, 0);
        u24box.addView(u24lab); u24box.addView(uptime24Val);
        LinearLayout u7box = new LinearLayout(this);
        u7box.setOrientation(LinearLayout.VERTICAL);
        u7box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView u7lab = new TextView(this);
        u7lab.setText("Uptime 7d"); u7lab.setTextColor(SUB); u7lab.setTextSize(11);
        uptime7dVal = valText("—"); uptime7dVal.setTextSize(16); uptime7dVal.setPadding(0, 2, 0, 0);
        u7box.addView(u7lab); u7box.addView(uptime7dVal);
        uptimeRow.addView(u24box); uptimeRow.addView(u7box);
        c10.addView(uptimeRow);
        row6.addView(c10);
        contentArea.addView(row6);

        refreshStatus();
    }

    private TextView valText(String v) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextColor(TXT);
        t.setTextSize(20);
        t.setPadding(0, 8, 0, 0);
        return t;
    }

    // ---------- Groups ----------
    private void buildGroupsTab() {
        contentArea.addView(sectionTitle("DANH SÁCH NHÓM"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 0, 24, 24);
        contentArea.addView(list);
        loadGroups(list);
    }

    private void loadGroups(LinearLayout list) {
        list.removeAllViews();
        list.addView(emptyText("Đang tải..."));
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/groups.php", null, sessionId);
            if (res == null) {
                ui.post(() -> { list.removeAllViews(); list.addView(retryText("Lỗi mạng, không tải được.", () -> loadGroups(list))); });
                return;
            }
            if (!res.optBoolean("linked", true)) {
                ui.post(() -> { list.removeAllViews(); list.addView(emptyText(NOT_LINKED_MSG)); });
                return;
            }
            groupsCache = res;
            ui.post(() -> {
                list.removeAllViews();
                Iterator<String> keys = res.keys();
                boolean any = false;
                while (keys.hasNext()) {
                    any = true;
                    String gid = keys.next();
                    JSONObject g = res.optJSONObject(gid);
                    String avatar = g == null ? null : g.optString("avatar", null);
                    if (avatar == null || avatar.isEmpty()) avatar = AVATAR_FALLBACK + gid;
                    list.addView(buildItemRow(
                            g == null ? gid : g.optString("name", gid),
                            g == null ? "" : (g.optInt("member_count", 0) + " thành viên"),
                            avatar, () -> openGroupDetail(gid)));
                }
                if (!any) list.addView(emptyText("Chưa có nhóm nào."));
            });
        });
    }

    private void openGroupDetail(String gid) {
        currentGroupId = gid;
        JSONObject g = groupsCache.optJSONObject(gid);
        if (g == null) g = new JSONObject();
        contentArea.removeAllViews();

        Button back = backButton("← Quay lại danh sách nhóm", () -> switchTab("groups"));
        contentArea.addView(back);

        contentArea.addView(detailRow("ID nhóm", gid));
        String role = g.optString("bot_role", "");
        String botOwnerTxt;
        if ("owner".equals(role)) botOwnerTxt = "🥇 Trưởng nhóm (key vàng)";
        else if ("deputy".equals(role)) botOwnerTxt = "🥈 Phó nhóm (key bạc)";
        else if ("member".equals(role)) botOwnerTxt = "👤 Thành viên thường — không có quyền quản trị";
        else botOwnerTxt = "- (chưa xác định)";
        contentArea.addView(detailRow("Quyền bot trong nhóm", botOwnerTxt));
        contentArea.addView(detailRow("Chủ nhóm", g.optString("owner_name", "-")));
        contentArea.addView(detailRow("Tên nhóm", g.optString("name", gid)));
        contentArea.addView(detailRow("Số thành viên", String.valueOf(g.optInt("member_count", 0))));
        contentArea.addView(detailRow("Bot hoạt động từ", fmtTime(g.optLong("added_at", 0))));
        contentArea.addView(detailRow("Antilink", g.optBoolean("antilink", false) ? "Bật" : "Tắt"));
        contentArea.addView(detailRow("Welcome", g.optBoolean("welcome", false) ? "Bật" : "Tắt"));
        contentArea.addView(detailRow("React tự động", g.optBoolean("react", false) ? "Bật" : "Tắt"));

        Button toggleAntilink = actionButton("Bật/tắt Antilink", () -> sendGroupCmd("toggle_antilink"));
        Button toggleWelcome = actionButton("Bật/tắt Welcome", () -> sendGroupCmd("toggle_welcome"));
        Button toggleReact = actionButton("Bật/tắt React", () -> sendGroupCmd("toggle_react"));
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        addRowItem(toggleRow, toggleAntilink);
        addRowItem(toggleRow, toggleWelcome);
        addRowItem(toggleRow, toggleReact);
        contentArea.addView(toggleRow);

        Button refreshInfoBtn = actionButton("🔄 Làm mới thông tin nhóm (bỏ qua cache)", () -> sendGroupCmd("refresh_group_info"));
        contentArea.addView(refreshInfoBtn);

        contentArea.addView(sectionTitle("GỬI TIN NHẮN TỚI NHÓM NÀY"));
        final EditText msgInput = new EditText(this);
        msgInput.setHint("Nhập nội dung...");
        msgInput.setHintTextColor(SUB);
        msgInput.setTextColor(TXT);
        msgInput.setMinLines(3);
        msgInput.setGravity(Gravity.TOP | Gravity.START);
        msgInput.setBackground(roundedBg(CARD, 10));
        msgInput.setPadding(28, 20, 28, 20);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.leftMargin = 24; msgLp.rightMargin = 24; msgLp.bottomMargin = 12;
        msgInput.setLayoutParams(msgLp);
        contentArea.addView(msgInput);

        Button sendMsgBtn = actionButton("📤 Gửi", () -> {
            String text = msgInput.getText().toString().trim();
            if (text.isEmpty()) return;
            sendGroupMessage(gid, text);
            msgInput.setText("");
        });
        sendMsgBtn.setBackground(roundedBg(ACC, 10));
        sendMsgBtn.setTextColor(ACC_TEXT);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sendLp.leftMargin = 24; sendLp.rightMargin = 24; sendLp.bottomMargin = 24;
        sendMsgBtn.setLayoutParams(sendLp);
        contentArea.addView(sendMsgBtn);
    }

    private void sendGroupMessage(String gid, String text) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "send_group_message");
                body.put("group_id", gid);
                JSONObject params = new JSONObject();
                params.put("text", text);
                body.put("params", params);
                httpJson("POST", "/commands.php", body, sessionId);
                ui.post(() -> android.widget.Toast.makeText(this, "Đã gửi lệnh, bot sẽ gửi tin nhắn trong giây lát.", android.widget.Toast.LENGTH_SHORT).show());
            } catch (Exception ignored) {}
        });
    }

    private void sendGroupCmd(String action) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", action);
                body.put("group_id", currentGroupId);
                httpJson("POST", "/commands.php", body, sessionId);
            } catch (Exception ignored) {}
        });
    }

    // ---------- Friends ----------
    private void buildFriendsTab() {
        contentArea.addView(sectionTitle("DANH SÁCH BẠN BÈ"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 0, 24, 24);
        contentArea.addView(list);
        loadFriends(list);
    }

    private void loadFriends(LinearLayout list) {
        list.removeAllViews();
        list.addView(emptyText("Đang tải..."));
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/friends.php", null, sessionId);
            if (res == null) {
                ui.post(() -> { list.removeAllViews(); list.addView(retryText("Lỗi mạng, không tải được.", () -> loadFriends(list))); });
                return;
            }
            if (!res.optBoolean("linked", true)) {
                ui.post(() -> { list.removeAllViews(); list.addView(emptyText(NOT_LINKED_MSG)); });
                return;
            }
            friendsCache = res;
            ui.post(() -> {
                list.removeAllViews();
                Iterator<String> keys = res.keys();
                boolean any = false;
                while (keys.hasNext()) {
                    any = true;
                    String uid = keys.next();
                    JSONObject f = res.optJSONObject(uid);
                    String perm = f == null ? "member" : f.optString("permission", "member");
                    String avatar = f == null ? null : f.optString("avatar", null);
                    if (avatar == null || avatar.isEmpty()) avatar = AVATAR_FALLBACK + uid;
                    list.addView(buildItemRow(
                            f == null ? uid : f.optString("name", uid),
                            permLabel(perm),
                            avatar, () -> openFriendDetail(uid),
                            true, permColor(perm)));
                }
                if (!any) list.addView(emptyText("Chưa có dữ liệu bạn bè."));
            });
        });
    }

    private String permLabel(String p) {
        if ("owner".equals(p)) return "👑 Admin chính";
        if ("admin".equals(p)) return "🔑 Admin";
        return "Thành viên";
    }

    // Ba dạng quyền -> 3 màu khác nhau, đồng bộ với badge màu bên bản web.
    private int permColor(String p) {
        if ("owner".equals(p)) return Color.parseColor("#e0a800"); // vàng gold - admin chính
        if ("admin".equals(p)) return ACC;                          // xanh - admin thường
        return SUB;                                                 // xám - thành viên
    }

    private void sendFriendPermCmd(String uid, String level) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "set_permission");
                JSONObject params = new JSONObject();
                params.put("uid", uid);
                params.put("level", level);
                body.put("params", params);
                httpJson("POST", "/commands.php", body, sessionId);
                ui.post(() -> {
                    android.widget.Toast.makeText(this, "Đã gửi lệnh đổi quyền, đang cập nhật...", android.widget.Toast.LENGTH_SHORT).show();
                    contentArea.postDelayed(() -> openFriendDetail(uid), 1500);
                });
            } catch (Exception ignored) {}
        });
    }

    private void openFriendDetail(String uid) {
        currentFriendId = uid;
        JSONObject f = friendsCache.optJSONObject(uid);
        if (f == null) f = new JSONObject();
        String perm = f.optString("permission", "member");
        contentArea.removeAllViews();
        contentArea.addView(backButton("← Quay lại danh sách bạn bè", () -> switchTab("friends")));
        contentArea.addView(detailRowWithCopy("ID", uid));
        contentArea.addView(detailRow("Tên", f.optString("name", uid)));

        LinearLayout permRow = detailRow("Quyền (bấm để đổi)", permLabel(perm), permColor(perm));
        LinearLayout permMenu = new LinearLayout(this);
        permMenu.setOrientation(LinearLayout.HORIZONTAL);
        permMenu.setPadding(24, 8, 24, 8);
        permMenu.setVisibility(View.GONE);
        permRow.setOnClickListener(v -> permMenu.setVisibility(
                permMenu.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        contentArea.addView(permRow);

        if (!"owner".equals(perm)) {
            Button toOwner = actionButton("👑 Đặt làm Admin chính", () -> sendFriendPermCmd(uid, "owner"));
            addRowItem(permMenu, toOwner);
        }
        if (!"admin".equals(perm)) {
            Button toAdmin = actionButton("🔑 Đặt làm Admin thường", () -> sendFriendPermCmd(uid, "admin"));
            addRowItem(permMenu, toAdmin);
        }
        if (!"member".equals(perm)) {
            Button toMember = actionButton("🙋 Hạ xuống Thành viên", () -> sendFriendPermCmd(uid, "member"));
            addRowItem(permMenu, toMember);
        }
        contentArea.addView(permMenu);

        onlineStatusView = detailRow("Trạng thái", friendStatusText(f), SUB);
        contentArea.addView(onlineStatusView);
        refreshFriendStatus(uid);

        boolean blocked = f.optBoolean("blocked", false);
        int blockedColor = blocked ? BAD : OK;
        contentArea.addView(detailRow("Cho phép dùng bot", blocked ? "🚫 Đang bị chặn" : "✅ Bình thường", blockedColor));
        Button toggleBlock = actionButton(blocked ? "✅ Mở lại quyền dùng bot" : "🚫 Chặn dùng bot",
                () -> sendUserBlockCmd(uid));
        if (blocked) toggleBlock.setTextColor(OK); else toggleBlock.setTextColor(BAD);
        contentArea.addView(toggleBlock);
    }

    private void addRowItem(LinearLayout row, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = 8;
        v.setLayoutParams(lp);
        row.addView(v);
    }

    private String friendStatusText(JSONObject f) {
        if (f.isNull("is_online") || !f.has("is_online")) return "Chưa xác định (đang kiểm tra...)";
        Boolean online = f.optBoolean("is_online", false);
        if (f.optBoolean("is_online", false)) return "🟢 Đang online";
        long last = f.optLong("last_active", 0);
        return last > 0 ? ("⚪ Offline · lần cuối " + fmtTime(last)) : "⚪ Offline";
    }

    private void refreshFriendStatus(String uid) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "refresh_friend_status");
                JSONObject params = new JSONObject();
                params.put("uid", uid);
                body.put("params", params);
                httpJson("POST", "/commands.php", body, sessionId);
                Thread.sleep(2500);
                JSONObject res = httpJson("GET", "/friends.php", null, sessionId);
                if (res != null && res.optBoolean("linked", true)) {
                    friendsCache = res;
                    JSONObject f = friendsCache.optJSONObject(uid);
                    if (f != null && onlineStatusView != null && uid.equals(currentFriendId)) {
                        String txt = friendStatusText(f);
                        ui.post(() -> {
                            TextView vt = (TextView) onlineStatusView.getChildAt(1);
                            if (vt != null) vt.setText(txt);
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private void sendUserBlockCmd(String uid) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "toggle_user_block");
                JSONObject params = new JSONObject();
                params.put("uid", uid);
                body.put("params", params);
                httpJson("POST", "/commands.php", body, sessionId);
                ui.post(() -> {
                    android.widget.Toast.makeText(this, "Đã gửi lệnh, đang cập nhật...", android.widget.Toast.LENGTH_SHORT).show();
                    contentArea.postDelayed(() -> openFriendDetail(uid), 1500);
                });
            } catch (Exception ignored) {}
        });
    }

    // ---------- Commands (module list) ----------
    private void buildCommandsTab() {
        contentArea.addView(sectionTitle("DANH SÁCH LỆNH / MODULE"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 0, 24, 24);
        contentArea.addView(list);
        list.addView(emptyText("Đang tải..."));
        io.execute(() -> {
            JSONObject wrapped = httpJsonArrayAsObject("/modules.php");
            ui.post(() -> {
                list.removeAllViews();
                if (wrapped == null) { list.addView(retryText("Lỗi mạng, không tải được.", () -> switchTab("commands"))); return; }
                if (wrapped.optBoolean("_linked_false", false)) {
                    list.addView(emptyText(NOT_LINKED_MSG)); return;
                }
                JSONArray arr = wrapped == null ? null : wrapped.optJSONArray("_arr");
                if (arr == null || arr.length() == 0) { list.addView(emptyText("Chưa có dữ liệu.")); return; }
                for (int i = 0; i < arr.length(); i++) {
                    // Bot mới gửi object {file,name,cmd,icon}; bot cũ (chưa cập
                    // nhật) có thể vẫn gửi chuỗi thô kiểu "modules.xxx" -> tự
                    // dựng tên hiển thị tạm, bỏ tiền tố "modules." đi.
                    JSONObject m = arr.optJSONObject(i);
                    String title, meta, icon, desc;
                    if (m != null) {
                        icon = m.optString("icon", "🧩");
                        title = icon + "  " + m.optString("name", m.optString("file", "?"));
                        meta = m.optString("cmd", "");
                        desc = m.optString("desc", "");
                    } else {
                        String raw = arr.optString(i, "");
                        String file = raw.replaceFirst("^modules\\.", "");
                        title = "🧩  " + prettifyFileName(file);
                        meta = "";
                        desc = "";
                    }
                    String cmdForDialog = meta;
                    String descForDialog = desc;
                    String titleForDialog = title;
                    Runnable onTap = () -> showCommandGuide(titleForDialog, descForDialog, cmdForDialog);
                    list.addView(buildItemRow(title, meta, null, onTap, false));
                }
            });
        });
    }

    private void showCommandGuide(String title, String desc, String cmd) {
        boolean hasCmd = cmd != null && !cmd.isEmpty() && !cmd.equals("tự động");
        StringBuilder body = new StringBuilder();
        if (desc != null && !desc.isEmpty()) body.append(desc).append("\n\n");
        body.append(hasCmd ? ("Cách dùng: gõ " + cmd + " trong Zalo.") : "Tính năng này chạy tự động, không cần gõ lệnh.");

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedBg(CARD, 16));
        box.setPadding(48, 44, 48, 36);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(TXT);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.bottomMargin = 20;
        box.addView(t, tLp);

        TextView d = new TextView(this);
        d.setText(body.toString());
        d.setTextColor(SUB);
        d.setTextSize(13);
        d.setLineSpacing(6, 1f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.bottomMargin = 28;
        box.addView(d, dLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        if (hasCmd) {
            Button copyBtn = new Button(this);
            copyBtn.setText("📋 Copy lệnh");
            copyBtn.setTextColor(ACC_TEXT);
            copyBtn.setBackground(roundedBg(ACC, 10));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            cLp.rightMargin = 8;
            copyBtn.setLayoutParams(cLp);
            copyBtn.setOnClickListener(v -> { copyCommandToClipboard(cmd); dialog.dismiss(); });
            btnRow.addView(copyBtn);
        }
        Button closeBtn = new Button(this);
        closeBtn.setText("Đóng");
        closeBtn.setTextColor(TXT);
        closeBtn.setBackground(roundedBg(BG, 10));
        LinearLayout.LayoutParams clLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        closeBtn.setLayoutParams(clLp);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(closeBtn);
        box.addView(btnRow);

        dialog.setContentView(box);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86);
            w.setAttributes(lp);
        }
        dialog.show();
    }

    private void copyCommandToClipboard(String cmd) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("lệnh bot", cmd));
        android.widget.Toast.makeText(this, "✅ Đã copy: " + cmd, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String prettifyFileName(String file) {
        String s = file.replace("_", " ").replace("-", " ");
        if (s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    // ---------- Control ----------
    private void buildControlTab() {
        contentArea.addView(sectionTitle("ĐIỀU KHIỂN BOT"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 0, 24, 24);
        box.addView(actionButton("🔇 Bật/tắt chế độ im lặng", () -> sendGlobalCmd("toggle_quiet")));
        box.addView(actionButton("🔄 Reload lại modules", () -> sendGlobalCmd("reload_modules")));
        box.addView(actionButton("👥 Làm mới danh sách bạn bè", () -> sendGlobalCmd("refresh_friends")));
        box.addView(actionButton("💬 Bật/tắt lệnh nhắn riêng (DM)", () -> sendGlobalCmd("toggle_dm")));

        Button stopBtn = actionButton("⏹️ Dừng bot từ xa", () -> confirmDanger(
                "Bot sẽ NGỪNG HOẠT ĐỘNG hoàn toàn cho tới khi bật lại thủ công trên máy. Chắc chắn dừng bot?",
                "stop_bot"));
        stopBtn.setTextColor(BAD);
        box.addView(stopBtn);

        Button restartBtn = actionButton("♻️ Khởi động lại bot từ xa", () -> confirmDanger(
                "Bot sẽ khởi động lại tiến trình (mất vài giây gián đoạn). Chắc chắn khởi động lại?",
                "restart_bot"));
        restartBtn.setTextColor(BAD);
        box.addView(restartBtn);

        Button logoutBtn = actionButton("🚪 Đăng xuất", this::logout);
        logoutBtn.setTextColor(BAD);
        box.addView(logoutBtn);
        contentArea.addView(box);
    }

    private void confirmDanger(String message, String action) {
        showConfirmDialog(message, () -> sendGlobalCmd(action));
    }

    /** Popup xác nhận tự vẽ đúng theme (thay AlertDialog mặc định của hệ thống — xấu, không ăn theo dark/light). */
    private void showConfirmDialog(String message, Runnable onConfirm) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(roundedBg(CARD, 16));
        box.setPadding(48, 44, 48, 36);

        TextView t = new TextView(this);
        t.setText(message);
        t.setTextColor(TXT);
        t.setTextSize(14);
        t.setLineSpacing(6, 1f);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.bottomMargin = 28;
        box.addView(t, tLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button okBtn = new Button(this);
        okBtn.setText("Đồng ý");
        okBtn.setTextColor(Color.WHITE);
        okBtn.setBackground(roundedBg(BAD, 10));
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        okLp.rightMargin = 8;
        okBtn.setLayoutParams(okLp);
        okBtn.setOnClickListener(v -> { onConfirm.run(); dialog.dismiss(); });
        btnRow.addView(okBtn);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("Huỷ");
        cancelBtn.setTextColor(TXT);
        cancelBtn.setBackground(roundedBg(BG, 10));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cancelBtn.setLayoutParams(cLp);
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(cancelBtn);
        box.addView(btnRow);

        dialog.setContentView(box);
        android.view.Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86);
            w.setAttributes(lp);
        }
        dialog.show();
    }

    private void sendGlobalCmd(String action) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", action);
                JSONObject res = httpJson("POST", "/commands.php", body, sessionId);
                boolean ok = res != null && res.optBoolean("ok", false);
                String msg = res != null ? res.optString("message", "") : "";
                if (!ok) {
                    String finalMsg = msg.isEmpty() ? "Gửi lệnh thất bại." : msg;
                    ui.post(() -> android.widget.Toast.makeText(this, "❌ " + finalMsg, android.widget.Toast.LENGTH_LONG).show());
                }
            } catch (Exception ignored) {}
        });
    }

    // ---------- Settings (token) ----------
    private Runnable tokPollRunnable = null;

    private void buildSettingsTab() {
        contentArea.addView(sectionTitle("TOKEN QUẢN LÝ BOT"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 24, 28, 28);
        box.setBackground(roundedBg(CARD, 16));
        LinearLayout.LayoutParams boxOuterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxOuterLp.leftMargin = 24; boxOuterLp.rightMargin = 24; boxOuterLp.bottomMargin = 8;
        box.setLayoutParams(boxOuterLp);

        TextView status = new TextView(this);
        status.setTextSize(12);
        status.setPadding(0, 0, 0, 16);
        box.addView(status);

        // -- Khối hiện khi ĐÃ liên kết --
        LinearLayout linkedBox = new LinearLayout(this);
        linkedBox.setOrientation(LinearLayout.VERTICAL);
        linkedBox.setVisibility(View.GONE);
        TextView val = new TextView(this);
        val.setTextColor(SUB);
        val.setText("-");
        linkedBox.addView(val);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button changeBtn = actionButton("🔁 Đổi token", null);
        btnRow.addView(changeBtn);
        linkedBox.addView(btnRow);
        box.addView(linkedBox);

        // -- Khối hiện khi CHƯA liên kết --
        LinearLayout unlinkedBox = new LinearLayout(this);
        unlinkedBox.setOrientation(LinearLayout.VERTICAL);
        EditText tokenInput = new EditText(this);
        tokenInput.setHint("Dán token quản lý bot (gõ .token trong Zalo)");
        styleInput(tokenInput);
        unlinkedBox.addView(tokenInput);
        Button linkBtn = actionButton("🔗 Liên kết token", null);
        unlinkedBox.addView(linkBtn);
        TextView hint = new TextView(this);
        hint.setTextColor(SUB);
        hint.setTextSize(12);
        hint.setPadding(0, 8, 0, 0);
        hint.setText("Sau khi bấm Liên kết, mở Zalo — vào đúng \"My Documents\" (Cloud của tôi) của CHÍNH tài khoản bot — gõ \".dongy\" để duyệt (không cần đợi bot nhắn gì trước, tự gõ luôn). Tài khoản Zalo khác không xác nhận được.");
        unlinkedBox.addView(hint);
        box.addView(unlinkedBox);

        contentArea.addView(box);

        linkBtn.setOnClickListener(v -> {
            String token = tokenInput.getText().toString().trim();
            if (token.isEmpty()) return;
            io.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("token", token);
                    JSONObject res = httpJson("POST", "/token_manage.php", body, sessionId);
                    boolean ok = res != null && res.optBoolean("ok", false);
                    String msg = res != null ? res.optString("message", "") : "";
                    ui.post(() -> {
                        if (ok) tokenInput.setText("");
                        else android.widget.Toast.makeText(this, "❌ " + (msg.isEmpty() ? "Token không khớp." : msg), android.widget.Toast.LENGTH_LONG).show();
                        loadTokenStatus(val, status, linkedBox, unlinkedBox, tokenInput);
                    });
                } catch (Exception ignored) {}
            });
        });

        changeBtn.setOnClickListener(v -> showConfirmDialog("Đổi sang token khác sẽ HUỶ liên kết hiện tại. Tiếp tục?",
                () -> doUnlinkToken(true, val, status, linkedBox, unlinkedBox, tokenInput)));

        loadTokenStatus(val, status, linkedBox, unlinkedBox, tokenInput);

        if ("admin".equals(currentRole)) {
            contentArea.addView(sectionTitle("MÃ MỜI (CHỈ ADMIN THẤY)"));
            LinearLayout inviteBox = new LinearLayout(this);
            inviteBox.setOrientation(LinearLayout.VERTICAL);
            inviteBox.setPadding(28, 24, 28, 28);
            inviteBox.setBackground(roundedBg(CARD, 16));
            LinearLayout.LayoutParams inviteOuterLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            inviteOuterLp.leftMargin = 24; inviteOuterLp.rightMargin = 24; inviteOuterLp.bottomMargin = 8;
            inviteBox.setLayoutParams(inviteOuterLp);
            Button createInviteBtn = actionButton("➕ Tạo mã mời mới", null);
            inviteBox.addView(createInviteBtn);
            TextView inviteList = new TextView(this);
            inviteList.setTextColor(SUB);
            inviteList.setTextSize(12);
            inviteList.setLineSpacing(6, 1f);
            inviteList.setText("Đang tải...");
            inviteBox.addView(inviteList);
            contentArea.addView(inviteBox);
            createInviteBtn.setOnClickListener(v -> createInvite(inviteList));
            loadInvites(inviteList);

            contentArea.addView(sectionTitle("QUẢN LÝ TÀI KHOẢN"));
            LinearLayout accountsBox = new LinearLayout(this);
            accountsBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams accOuterLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            accOuterLp.leftMargin = 24; accOuterLp.rightMargin = 24; accOuterLp.bottomMargin = 24;
            accountsBox.setLayoutParams(accOuterLp);
            contentArea.addView(accountsBox);
            loadAccounts(accountsBox);
        }
    }

    /** Danh sách toàn bộ tài khoản (chỉ admin thấy) — bật/tắt (khoá) quyền
     * đăng nhập của từng tài khoản thường. Không cho khoá chính admin. */
    private void loadAccounts(LinearLayout box) {
        io.execute(() -> {
            JSONObject res = null;
            try {
                JSONObject body = new JSONObject();
                body.put("action", "list_accounts");
                res = httpJson("POST", "/auth.php", body, sessionId);
            } catch (Exception ignored) {}
            JSONArray accs = res != null ? res.optJSONArray("accounts") : null;
            final JSONArray finalAccs = accs;
            ui.post(() -> {
                box.removeAllViews();
                if (finalAccs == null || finalAccs.length() == 0) {
                    TextView t = new TextView(this);
                    t.setTextColor(SUB);
                    t.setTextSize(12);
                    t.setText("Chưa tải được danh sách tài khoản.");
                    box.addView(t);
                    return;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());
                for (int i = 0; i < finalAccs.length(); i++) {
                    JSONObject a = finalAccs.optJSONObject(i);
                    if (a == null) continue;
                    String uname = a.optString("username", "");
                    String role = a.optString("role", "member");
                    boolean disabled = a.optBoolean("disabled", false);
                    long createdAt = a.optLong("created_at", 0);

                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setBackground(roundedBg(CARD, 12));
                    row.setPadding(24, 18, 24, 18);
                    LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowLp.bottomMargin = 10;
                    row.setLayoutParams(rowLp);

                    LinearLayout info = new LinearLayout(this);
                    info.setOrientation(LinearLayout.VERTICAL);
                    info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    TextView nameT = new TextView(this);
                    nameT.setText(uname + ("admin".equals(role) ? "  👑" : ""));
                    nameT.setTextColor(TXT);
                    nameT.setTextSize(14);
                    nameT.setTypeface(null, android.graphics.Typeface.BOLD);
                    info.addView(nameT);
                    TextView subT = new TextView(this);
                    subT.setTextColor(disabled ? BAD : SUB);
                    subT.setTextSize(11);
                    subT.setText(("admin".equals(role) ? "Admin" : "Thành viên") + " · "
                            + (disabled ? "🚫 Đã khoá" : "✅ Hoạt động") + " · "
                            + sdf.format(new Date(createdAt * 1000L)));
                    info.addView(subT);
                    row.addView(info);

                    if (!"admin".equals(role)) {
                        Button toggleBtn = new Button(this);
                        toggleBtn.setText(disabled ? "Mở khoá" : "Khoá");
                        toggleBtn.setTextColor(Color.WHITE);
                        toggleBtn.setBackground(roundedBg(disabled ? OK : BAD, 10));
                        toggleBtn.setMinWidth(0);
                        toggleBtn.setMinHeight(0);
                        toggleBtn.setPadding(24, 10, 24, 10);
                        toggleBtn.setTextSize(12);
                        toggleBtn.setElevation(0);
                        applyPressFeedback(toggleBtn);
                        toggleBtn.setOnClickListener(v -> toggleAccount(uname, box));
                        row.addView(toggleBtn);
                    }
                    box.addView(row);
                }
            });
        });
    }

    private void toggleAccount(String username, LinearLayout box) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "toggle_account");
                body.put("username", username);
                httpJson("POST", "/auth.php", body, sessionId);
            } catch (Exception ignored) {}
            ui.post(() -> loadAccounts(box));
        });
    }

    private void createInvite(TextView inviteList) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "create_invite");
                JSONObject res = httpJson("POST", "/auth.php", body, sessionId);
                if (res != null && res.optBoolean("ok", false)) {
                    String code = res.optString("code", "");
                    ui.post(() -> {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("✅ Mã mời mới")
                                .setMessage(code + "\n\nĐưa mã này cho người bạn muốn mời — dùng được 1 lần.")
                                .setPositiveButton("OK", null).show();
                        loadInvites(inviteList);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void loadInvites(TextView inviteList) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "list_invites");
                JSONObject res = httpJson("POST", "/auth.php", body, sessionId);
                JSONArray invites = res != null ? res.optJSONArray("invites") : null;
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());
                StringBuilder sb = new StringBuilder();
                if (invites == null || invites.length() == 0) {
                    sb.append("Chưa có mã mời nào.");
                } else {
                    for (int i = 0; i < invites.length(); i++) {
                        JSONObject iv = invites.optJSONObject(i);
                        if (iv == null) continue;
                        String usedBy = iv.optString("used_by", "");
                        String status = usedBy.isEmpty() || "null".equals(usedBy) ? "⏳ Chưa dùng" : ("✅ Đã dùng bởi " + usedBy);
                        sb.append(iv.optString("code")).append(" · ").append(status)
                          .append(" · ").append(sdf.format(new Date(iv.optLong("created_at", 0) * 1000L))).append("\n");
                    }
                }
                ui.post(() -> inviteList.setText(sb.toString().trim()));
            } catch (Exception ignored) {}
        });
    }

    private void doUnlinkToken(boolean silent, TextView val, TextView status, LinearLayout linkedBox, LinearLayout unlinkedBox, EditText tokenInput) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "unlink");
                httpJson("POST", "/token_manage.php", body, sessionId);
            } catch (Exception ignored) {}
            ui.post(() -> {
                if (!silent) android.widget.Toast.makeText(this, "Đã xoá liên kết.", android.widget.Toast.LENGTH_SHORT).show();
                loadTokenStatus(val, status, linkedBox, unlinkedBox, tokenInput);
            });
        });
    }

    private void loadTokenStatus(TextView val, TextView status, LinearLayout linkedBox, LinearLayout unlinkedBox, EditText tokenInput) {
        if (tokPollRunnable != null) { ui.removeCallbacks(tokPollRunnable); tokPollRunnable = null; }
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/token_manage.php", null, sessionId);
            ui.post(() -> {
                boolean linked = res != null && res.optBoolean("linked", false);
                boolean pending = res != null && res.optBoolean("pending", false);
                String pendingStatus = res != null ? res.optString("pending_status", "") : "";

                linkedBox.setVisibility(linked ? View.VISIBLE : View.GONE);
                unlinkedBox.setVisibility(linked ? View.GONE : View.VISIBLE);

                if (linked) {
                    val.setText(res.optString("token", "-"));
                    status.setText("✅ Đã liên kết");
                    status.setTextColor(OK);
                    return;
                }

                tokenInput.setEnabled(!pending);
                if (pending) {
                    status.setText("⏳ Đang chờ chính bot xác nhận trong Zalo (self-chat, \".dongy\"/\".khongdongy\")...");
                    status.setTextColor(BAD);
                    tokPollRunnable = () -> loadTokenStatus(val, status, linkedBox, unlinkedBox, tokenInput);
                    ui.postDelayed(tokPollRunnable, 3000);
                } else if ("denied".equals(pendingStatus)) {
                    status.setText("❌ Bot vừa từ chối yêu cầu liên kết trước đó.");
                    status.setTextColor(BAD);
                } else if ("expired".equals(pendingStatus)) {
                    status.setText("⌛ Yêu cầu trước đã hết hạn (quá 5 phút không xác nhận).");
                    status.setTextColor(BAD);
                } else {
                    status.setText("⚠️ Chưa liên kết");
                    status.setTextColor(BAD);
                }
            });
        });
    }

    // ---------- Log ----------
    private void buildLogTab() {
        contentArea.addView(sectionTitle("LOG BOT"));
        TextView logView = new TextView(this);
        logView.setTextColor(SUB);
        logView.setTextSize(11);
        logView.setPadding(24, 0, 24, 24);
        logView.setText("Đang tải...");
        contentArea.addView(logView);
        io.execute(() -> {
            JSONObject wrapped = httpJsonArrayAsObject("/log.php");
            ui.post(() -> {
                if (wrapped != null && wrapped.optBoolean("_linked_false", false)) {
                    logView.setText(NOT_LINKED_MSG); return;
                }
                JSONArray arr = wrapped == null ? null : wrapped.optJSONArray("_arr");
                if (arr == null || arr.length() == 0) { logView.setText("Chưa có log."); return; }
                StringBuilder sb = new StringBuilder();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject l = arr.optJSONObject(i);
                    if (l == null) continue;
                    sb.append("[").append(sdf.format(new Date(l.optLong("t", 0) * 1000L))).append("] ")
                      .append(l.optString("msg")).append("\n");
                }
                logView.setText(sb.toString());
            });
        });
    }

    // ================= Shared UI helpers =================
    private static final Map<String, Bitmap> avatarCache = new ConcurrentHashMap<>();

    private void loadAvatarAsync(ImageView av, String url) {
        if (url == null || url.isEmpty() || !url.startsWith("http")) return;
        Bitmap cached = avatarCache.get(url);
        if (cached != null) { av.setImageBitmap(cached); return; }
        io.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
                if (bmp != null) {
                    avatarCache.put(url, bmp);
                    ui.post(() -> av.setImageBitmap(bmp));
                }
            } catch (Exception ignored) {
                // tải avatar lỗi (mạng/link hỏng) -> giữ nguyên ô xám mặc định, không báo lỗi ồn ào
            }
        });
    }

    private LinearLayout buildItemRow(String title, String meta, String avatarUrl, Runnable onClick) {
        return buildItemRow(title, meta, avatarUrl, onClick, true, null);
    }

    private LinearLayout buildItemRow(String title, String meta, String avatarUrl, Runnable onClick, boolean showAvatar) {
        return buildItemRow(title, meta, avatarUrl, onClick, showAvatar, null);
    }

    private LinearLayout buildItemRow(String title, String meta, String avatarUrl, Runnable onClick, boolean showAvatar, Integer metaColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(roundedBg(CARD, 12));
        row.setPadding(24, 24, 24, 24);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = 12;
        row.setLayoutParams(rowLp);

        if (showAvatar) {
            ImageView av = new ImageView(this);
            av.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
            av.setBackground(roundedBg(Color.parseColor("#2a3140"), 40));
            loadAvatarAsync(av, avatarUrl);
            row.addView(av);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(showAvatar ? 20 : 0, 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title); t.setTextColor(TXT); t.setTextSize(14);
        TextView m = new TextView(this);
        m.setText(meta); m.setTextColor(metaColor != null ? metaColor : SUB); m.setTextSize(12);
        textCol.addView(t); textCol.addView(m);
        row.addView(textCol);

        if (onClick != null) { row.setOnClickListener(v -> onClick.run()); applyPressFeedback(row); }
        return row;
    }

    private TextView emptyText(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(SUB); t.setGravity(Gravity.CENTER);
        t.setPadding(0, 40, 0, 40);
        return t;
    }

    private TextView retryText(String msg, Runnable onRetry) {
        TextView t = new TextView(this);
        t.setText("⚠️ " + msg + "\n\nChạm để thử lại");
        t.setTextColor(BAD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 40, 0, 40);
        t.setOnClickListener(v -> onRetry.run());
        return t;
    }

    private Button backButton(String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(ACC);
        b.setBackgroundColor(BG);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private Button actionButton(String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TXT);
        b.setBackground(roundedBg(CARD, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 12;
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        applyPressFeedback(b);
        return b;
    }

    /** Nền nút vẽ tay (roundedBg) không có ripple mặc định như Button hệ
     * thống -> tự thêm hiệu ứng mờ nhẹ khi nhấn giữ cho có phản hồi chạm. */
    private void applyPressFeedback(View v) {
        v.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.setAlpha(0.6f);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.setAlpha(1.0f);
                    break;
            }
            return false; // vẫn cho onClick chạy bình thường
        });
    }

    private LinearLayout detailRow(String k, String v) {
        return detailRow(k, v, null);
    }

    private LinearLayout detailRow(String k, String v, Integer valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(24, 16, 24, 16);
        TextView kt = new TextView(this);
        kt.setText(k); kt.setTextColor(SUB); kt.setTextSize(13);
        kt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView vt = new TextView(this);
        vt.setText(v); vt.setTextColor(valueColor != null ? valueColor : TXT); vt.setTextSize(13);
        row.addView(kt); row.addView(vt);
        return row;
    }

    /** Giống detailRow nhưng có thêm nút "Sao chép" cạnh giá trị — dùng cho ID. */
    private LinearLayout detailRowWithCopy(String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(24, 16, 24, 16);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView kt = new TextView(this);
        kt.setText(k); kt.setTextColor(SUB); kt.setTextSize(13);
        kt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView vt = new TextView(this);
        vt.setText(v); vt.setTextColor(TXT); vt.setTextSize(13);
        row.addView(kt); row.addView(vt);

        Button copyBtn = new Button(this);
        copyBtn.setText("📋 Sao chép");
        copyBtn.setTextColor(ACC);
        copyBtn.setBackground(roundedBg(BG, 8));
        copyBtn.setTextSize(11);
        copyBtn.setMinWidth(0);
        copyBtn.setMinHeight(0);
        copyBtn.setPadding(20, 6, 20, 6);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.leftMargin = 12;
        copyBtn.setLayoutParams(cbLp);
        copyBtn.setOnClickListener(view -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("ID", v));
            android.widget.Toast.makeText(this, "✅ Đã copy ID", android.widget.Toast.LENGTH_SHORT).show();
        });
        row.addView(copyBtn);
        return row;
    }

    private String fmtTime(long ts) {
        if (ts <= 0) return "-";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(ts * 1000L));
    }

    // ================= Polling =================
    private void refreshStatus() {
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/status.php", null, sessionId);
            if (res == null) return;
            ui.post(() -> {
                if (!res.optBoolean("linked", true)) {
                    statusDot.setTextColor(Color.parseColor("#666666"));
                    statusText.setText(" 🔒 Chưa liên kết");
                    if (uptimeVal != null) uptimeVal.setText("-");
                    if (sysVal != null) sysVal.setText("-");
                    if (groupsVal != null) groupsVal.setText("-");
                    if (friendsVal != null) friendsVal.setText("-");
                    if (pingVal != null) pingVal.setText("-");
                    if (apiLatencyVal != null) apiLatencyVal.setText("-");
                    if (softwareVal != null) softwareVal.setText("-");
                    if (softwareVerVal != null) softwareVerVal.setText("-");
                    if (startedAtVal != null) startedAtVal.setText("-");
                    return;
                }
                boolean online = res.optBoolean("online", false);
                statusDot.setTextColor(online ? OK : BAD);
                statusText.setText(online ? " Online" : " Offline");
                String botName = res.optString("bot_name", "");
                if (botTitleView != null && !botName.isEmpty()) botTitleView.setText(botName);
                if (uptimeVal != null) {
                    int s = res.optInt("uptime_sec", 0);
                    uptimeVal.setText(online ? (s/3600) + "h " + ((s%3600)/60) + "m" : "-");
                }
                if (sysVal != null) {
                    boolean hasCpu = !res.isNull("cpu_temp");
                    boolean hasBattery = !res.isNull("battery_temp");
                    String temp = hasCpu ? (res.optDouble("cpu_temp") + "°C")
                                : hasBattery ? (res.optDouble("battery_temp") + "°C") : "-";
                    String ram = res.isNull("ram_percent") ? "-" : res.optDouble("ram_percent") + "%";
                    sysVal.setText(temp + " / " + ram);
                }
                if (groupsVal != null) groupsVal.setText(String.valueOf(res.optInt("groups", 0)));
                if (friendsVal != null) friendsVal.setText(String.valueOf(res.optInt("friends", 0)));
                if (pingVal != null) {
                    int pm = res.optInt("ping_ms", -1);
                    pingVal.setText(pm >= 0 ? (pm + " ms") : "-");
                }
                if (apiLatencyVal != null) {
                    int al = res.optInt("api_latency_ms", -1);
                    apiLatencyVal.setText(al >= 0 ? (al + " ms") : "-");
                }
                if (softwareVal != null) {
                    String sw = res.optString("software", "");
                    softwareVal.setText(sw.isEmpty() ? "-" : sw);
                }
                if (softwareVerVal != null) {
                    String sv = res.optString("software_version", "");
                    softwareVerVal.setText(sv.isEmpty() ? "-" : ("Python " + sv));
                }
                if (startedAtVal != null) startedAtVal.setText(fmtTime(res.optLong("start_time", 0)));

                if (accTypeVal != null) {
                    // bot cũ chưa cập nhật backend -> field có thể không có, hiện "—" chứ không crash.
                    String accType = res.optString("zalo_account_type", "");
                    accTypeVal.setText(accType.equals("admin") ? "Admin"
                            : accType.equals("thuong") ? "Thường" : "—");
                }
            });
            maybeRefreshUptimeStats();
        });
    }

    /** uptime_stats không cần dày như refreshStatus() (500ms) -> tự throttle 30s,
     * vẫn cưỡi chung vòng lặp polling sẵn có, không tạo thêm timer song song. */
    private void maybeRefreshUptimeStats() {
        long now = System.currentTimeMillis();
        if (now - lastUptimeStatsCallMs < 30000) return;
        lastUptimeStatsCallMs = now;
        JSONObject res = httpJson("GET", "/status.php?action=uptime_stats", null, sessionId);
        ui.post(() -> {
            if (uptime24Val == null || uptime7dVal == null) return;
            if (res == null) {
                uptime24Val.setText("—");
                uptime7dVal.setText("—");
                android.util.Log.w("KhaiBot", "uptime_stats: request lỗi/null");
                return;
            }
            setUptimeVal(uptime24Val, res.optJSONObject("24h"));
            setUptimeVal(uptime7dVal, res.optJSONObject("7d"));
        });
    }

    private void setUptimeVal(TextView v, JSONObject stat) {
        if (stat == null) { v.setText("—"); return; }
        double pct = stat.optDouble("uptime_pct", Double.NaN);
        if (Double.isNaN(pct)) {
            v.setText("—");
            android.util.Log.w("KhaiBot", "uptime_stats: thiếu field uptime_pct");
            return;
        }
        v.setText(String.format(java.util.Locale.getDefault(), "%.1f%%", pct));
        v.setTextColor(pct >= 95 ? OK : pct >= 80 ? Color.parseColor("#e6a23c") : BAD);
    }

    // ================= HTTP helpers =================
    /** GET trả về mảng JSON -> bọc vào {"_arr": [...]} cho dễ dùng chung 1 kiểu trả về JSONObject. */
    private JSONObject httpJsonArrayAsObject(String path) {
        try {
            String raw = httpRaw("GET", path, null, sessionId);
            if (raw == null) return null;
            String trimmed = raw.trim();
            if (trimmed.startsWith("{")) {
                // Server trả object thay vì mảng -> chỉ xảy ra khi chưa liên kết
                // token ({"linked":false}) hoặc lỗi khác -> đánh dấu để UI hiện
                // đúng thông báo, không coi như "chưa có dữ liệu" chung chung.
                JSONObject obj = new JSONObject(trimmed);
                JSONObject wrap = new JSONObject();
                wrap.put("_linked_false", !obj.optBoolean("linked", true));
                return wrap;
            }
            JSONArray arr = new JSONArray(raw);
            JSONObject wrap = new JSONObject();
            wrap.put("_arr", arr);
            return wrap;
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject httpJson(String method, String path, JSONObject body, String session) {
        try {
            String raw = httpRaw(method, path, body, session);
            if (raw == null) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String httpRaw(String method, String path, JSONObject body, String session) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API_BASE + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Content-Type", "application/json");
            if (session != null) conn.setRequestProperty("X-SESSION-ID", session);
            if (body != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }
            }
            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String result = readStream(is);
            if (code == 401) {
                ui.post(this::logout);
            }
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readStream(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        polling = false;
        io.shutdownNow();
    }
}
