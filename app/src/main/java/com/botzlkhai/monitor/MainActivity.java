package com.botzlkhai.monitor;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Đổi đúng domain/host thật của mày
    private static final String API_BASE = "http://zrmteam.x10.mx/app-bot-zeplo/api";

    private static final int BG = Color.parseColor("#0b0e14");
    private static final int CARD = Color.parseColor("#151a24");
    private static final int ACC = Color.parseColor("#4f8cff");
    private static final int OK = Color.parseColor("#2ecc71");
    private static final int BAD = Color.parseColor("#e74c3c");
    private static final String NOT_LINKED_MSG =
            "🔒 Chưa liên kết token quản lý bot — vào tab Token, dán token rồi bấm Liên kết để xem được mục này.";
    private static final int TXT = Color.parseColor("#e6e9ef");
    private static final int SUB = Color.parseColor("#8b93a5");

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String sessionId;

    private FrameLayout root;
    private LinearLayout loginView, mainView;
    private TextView statusDot, statusText, uptimeVal, sysVal, groupsVal, friendsVal, cmdLogView;
    private LinearLayout contentArea;
    private JSONObject groupsCache = new JSONObject(), friendsCache = new JSONObject();
    private String currentGroupId, currentFriendId;
    private boolean polling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bot_khai_prefs", MODE_PRIVATE);
        sessionId = prefs.getString("session_id", null);

        root = new FrameLayout(this);
        setContentView(root);

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

        TextView title = new TextView(this);
        title.setText("🔐 Đăng nhập quản lý");
        title.setTextColor(TXT);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 40);
        loginView.addView(title);

        TextView err = new TextView(this);
        err.setTextColor(BAD);
        err.setTextSize(13);
        err.setPadding(0, 0, 0, 16);
        loginView.addView(err);

        EditText user = new EditText(this);
        user.setHint("Tài khoản");
        styleInput(user);
        loginView.addView(user);

        EditText pass = new EditText(this);
        pass.setHint("Mật khẩu");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(pass);
        loginView.addView(pass);

        Button loginBtn = new Button(this);
        loginBtn.setText("Đăng nhập");
        loginBtn.setTextColor(Color.WHITE);
        loginBtn.setBackgroundColor(ACC);
        loginBtn.setOnClickListener(v -> {
            String u = user.getText().toString().trim();
            String p = pass.getText().toString();
            if (u.isEmpty() || p.isEmpty()) { err.setText("Nhập đủ tài khoản và mật khẩu."); return; }
            doLogin(u, p, err);
        });
        loginView.addView(loginBtn);

        root.addView(loginView);
    }

    private void styleInput(EditText e) {
        e.setTextColor(TXT);
        e.setHintTextColor(SUB);
        e.setBackgroundColor(CARD);
        e.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 20;
        e.setLayoutParams(lp);
    }

    private void doLogin(String username, String password, TextView err) {
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                JSONObject res = httpJson("POST", "/auth.php", body, null);
                if (res != null && res.optBoolean("ok", false)) {
                    sessionId = res.getString("session_id");
                    prefs.edit().putString("session_id", sessionId).apply();
                    ui.post(this::showMain);
                } else {
                    ui.post(() -> err.setText("Sai tài khoản hoặc mật khẩu."));
                }
            } catch (Exception e) {
                ui.post(() -> err.setText("Lỗi kết nối server."));
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
        TextView title = new TextView(this);
        title.setText("🤖 Bot-ZL-Khai");
        title.setTextColor(TXT);
        title.setTextSize(18);
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
            b.setBackgroundColor(CARD);
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
        c.setBackgroundColor(CARD);
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
        LinearLayout c2 = card("Nhiệt độ / RAM");
        sysVal = valText("-"); c2.addView(sysVal);
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

        contentArea.addView(sectionTitle("LỆNH GẦN ĐÂY"));
        cmdLogView = new TextView(this);
        cmdLogView.setTextColor(SUB);
        cmdLogView.setPadding(32, 0, 32, 32);
        cmdLogView.setText("Chưa có gì.");
        contentArea.addView(cmdLogView);

        refreshStatus();
        refreshCmdLog();
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
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/groups.php", null, sessionId);
            if (res == null) return;
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
                    list.addView(buildItemRow(
                            g == null ? gid : g.optString("name", gid),
                            g == null ? "" : (g.optInt("member_count", 0) + " thành viên"),
                            null, () -> openGroupDetail(gid)));
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
        contentArea.addView(detailRow("Key bot", g.optString("key", gid)));
        contentArea.addView(detailRow("Tên nhóm", g.optString("name", gid)));
        contentArea.addView(detailRow("Số thành viên", String.valueOf(g.optInt("member_count", 0))));
        contentArea.addView(detailRow("Bot hoạt động từ", fmtTime(g.optLong("added_at", 0))));
        contentArea.addView(detailRow("Antilink", g.optBoolean("antilink", false) ? "Bật" : "Tắt"));
        contentArea.addView(detailRow("Welcome", g.optBoolean("welcome", false) ? "Bật" : "Tắt"));

        Button toggleAntilink = actionButton("Bật/tắt Antilink", () -> sendGroupCmd("toggle_antilink"));
        Button toggleWelcome = actionButton("Bật/tắt Welcome", () -> sendGroupCmd("toggle_welcome"));
        contentArea.addView(toggleAntilink);
        contentArea.addView(toggleWelcome);
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
        io.execute(() -> {
            JSONObject res = httpJson("GET", "/friends.php", null, sessionId);
            if (res == null) return;
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
                    list.addView(buildItemRow(
                            f == null ? uid : f.optString("name", uid),
                            f == null ? "member" : f.optString("permission", "member"),
                            null, () -> openFriendDetail(uid)));
                }
                if (!any) list.addView(emptyText("Chưa có dữ liệu bạn bè."));
            });
        });
    }

    private void openFriendDetail(String uid) {
        currentFriendId = uid;
        JSONObject f = friendsCache.optJSONObject(uid);
        if (f == null) f = new JSONObject();
        contentArea.removeAllViews();
        contentArea.addView(backButton("← Quay lại danh sách bạn bè", () -> switchTab("friends")));
        contentArea.addView(detailRow("ID", uid));
        contentArea.addView(detailRow("Tên", f.optString("name", uid)));
        contentArea.addView(detailRow("Quyền", f.optString("permission", "member")));
    }

    // ---------- Commands (module list) ----------
    private void buildCommandsTab() {
        contentArea.addView(sectionTitle("DANH SÁCH LỆNH / MODULE"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 0, 24, 24);
        contentArea.addView(list);
        io.execute(() -> {
            JSONObject wrapped = httpJsonArrayAsObject("/modules.php");
            ui.post(() -> {
                list.removeAllViews();
                if (wrapped != null && wrapped.optBoolean("_linked_false", false)) {
                    list.addView(emptyText(NOT_LINKED_MSG)); return;
                }
                JSONArray arr = wrapped == null ? null : wrapped.optJSONArray("_arr");
                if (arr == null || arr.length() == 0) { list.addView(emptyText("Chưa có dữ liệu.")); return; }
                for (int i = 0; i < arr.length(); i++) {
                    // Bot mới gửi object {file,name,cmd,icon}; bot cũ (chưa cập
                    // nhật) có thể vẫn gửi chuỗi thô kiểu "modules.xxx" -> tự
                    // dựng tên hiển thị tạm, bỏ tiền tố "modules." đi.
                    JSONObject m = arr.optJSONObject(i);
                    String title, meta, icon;
                    if (m != null) {
                        icon = m.optString("icon", "🧩");
                        title = icon + "  " + m.optString("name", m.optString("file", "?"));
                        meta = m.optString("cmd", "");
                    } else {
                        String raw = arr.optString(i, "");
                        String file = raw.replaceFirst("^modules\\.", "");
                        title = "🧩  " + prettifyFileName(file);
                        meta = "";
                    }
                    list.addView(buildItemRow(title, meta, null, null));
                }
            });
        });
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
        new android.app.AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("Đồng ý", (d, w) -> sendGlobalCmd(action))
                .setNegativeButton("Huỷ", null)
                .show();
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
        box.setPadding(24, 0, 24, 24);

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
        Button unlinkBtn = actionButton("🗑️ Xoá liên kết", null);
        unlinkBtn.setTextColor(BAD);
        btnRow.addView(changeBtn);
        btnRow.addView(unlinkBtn);
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
        hint.setText("Sau khi bấm Liên kết, mở Zalo — vào đúng đoạn \"Tin nhắn của tôi\" (self-chat) của CHÍNH tài khoản bot — trả lời \"dong y\" để duyệt. Tài khoản Zalo khác không xác nhận được.");
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

        unlinkBtn.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setMessage("Xoá liên kết token hiện tại?")
                .setPositiveButton("Đồng ý", (d, w) -> doUnlinkToken(false, val, status, linkedBox, unlinkedBox, tokenInput))
                .setNegativeButton("Huỷ", null).show());

        changeBtn.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setMessage("Đổi sang token khác sẽ HUỶ liên kết hiện tại. Tiếp tục?")
                .setPositiveButton("Đồng ý", (d, w) -> doUnlinkToken(true, val, status, linkedBox, unlinkedBox, tokenInput))
                .setNegativeButton("Huỷ", null).show());

        loadTokenStatus(val, status, linkedBox, unlinkedBox, tokenInput);
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
                    status.setText("⏳ Đang chờ chính bot xác nhận trong Zalo (self-chat, \"dong y\"/\"khong dong y\")...");
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
    private LinearLayout buildItemRow(String title, String meta, Bitmap avatar, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(CARD);
        row.setPadding(24, 24, 24, 24);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = 12;
        row.setLayoutParams(rowLp);

        ImageView av = new ImageView(this);
        av.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        av.setBackgroundColor(Color.parseColor("#2a3140"));
        if (avatar != null) av.setImageBitmap(avatar);
        row.addView(av);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(20, 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title); t.setTextColor(TXT); t.setTextSize(14);
        TextView m = new TextView(this);
        m.setText(meta); m.setTextColor(SUB); m.setTextSize(12);
        textCol.addView(t); textCol.addView(m);
        row.addView(textCol);

        if (onClick != null) row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private TextView emptyText(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(SUB); t.setGravity(Gravity.CENTER);
        t.setPadding(0, 40, 0, 40);
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
        b.setBackgroundColor(CARD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 12;
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private LinearLayout detailRow(String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(24, 16, 24, 16);
        TextView kt = new TextView(this);
        kt.setText(k); kt.setTextColor(SUB); kt.setTextSize(13);
        kt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView vt = new TextView(this);
        vt.setText(v); vt.setTextColor(TXT); vt.setTextSize(13);
        row.addView(kt); row.addView(vt);
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
                    return;
                }
                boolean online = res.optBoolean("online", false);
                statusDot.setTextColor(online ? OK : BAD);
                statusText.setText(online ? " Online" : " Offline");
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
            });
        });
    }

    private void refreshCmdLog() {
        io.execute(() -> {
            JSONObject wrapped = httpJsonArrayAsObject("/commands.php?pending=0");
            ui.post(() -> {
                if (cmdLogView == null) return;
                if (wrapped != null && wrapped.optBoolean("_linked_false", false)) {
                    cmdLogView.setText(NOT_LINKED_MSG); return;
                }
                JSONArray arr = wrapped == null ? null : wrapped.optJSONArray("_arr");
                if (arr == null || arr.length() == 0) { cmdLogView.setText("Chưa có gì."); return; }
                StringBuilder sb = new StringBuilder();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                for (int i = 0; i < Math.min(arr.length(), 20); i++) {
                    JSONObject c = arr.optJSONObject(i);
                    if (c == null) continue;
                    sb.append(c.optString("action")).append(" · ")
                      .append(sdf.format(new Date(c.optLong("created_at", 0) * 1000L)))
                      .append(" · ").append(c.optString("status")).append("\n");
                }
                cmdLogView.setText(sb.toString());
            });
        });
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
