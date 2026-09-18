package com.hihu.donatefloat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** Gọi thẳng tới donate_server.py (FastAPI chạy trên Termux). Không dùng
 * thư viện ngoài (OkHttp/Retrofit) để giữ build.gradle tối giản, dễ build
 * qua GitHub Actions không phải lo version conflict. */
public class ApiClient {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static class Transaction {
        public int id;
        public long amount;
        public String description;
        public String whenTime;
        public String matchedContent; // null nếu chưa khớp đơn nào
    }

    private static HttpURLConnection open(Context ctx, String path, String method) throws Exception {
        URL url = new URL(Prefs.serverUrl(ctx) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-Api-Key", Prefs.apiToken(ctx));
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        return conn;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    public static void createOrder(Context ctx, String content, Callback<String> cb) {
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(content, "UTF-8");
                HttpURLConnection conn = open(ctx, "/order?content=" + encoded, "POST");
                conn.setDoOutput(true);
                conn.getOutputStream().close();
                int code = conn.getResponseCode();
                InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String body = readAll(in);
                if (code >= 200 && code < 300) {
                    JSONObject obj = new JSONObject(body);
                    cb.onSuccess(obj.getString("qr_url"));
                } else {
                    cb.onError("Lỗi server (" + code + "): " + body);
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public static void getTransactions(Context ctx, int sinceId, Callback<Transaction[]> cb) {
        getTransactions(ctx, sinceId, 50, cb);
    }

    public static void getTransactions(Context ctx, int sinceId, int limit, Callback<Transaction[]> cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = open(ctx, "/transactions?since_id=" + sinceId + "&limit=" + limit, "GET");
                int code = conn.getResponseCode();
                InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String body = readAll(in);
                if (code >= 200 && code < 300) {
                    JSONObject obj = new JSONObject(body);
                    JSONArray arr = obj.getJSONArray("transactions");
                    Transaction[] result = new Transaction[arr.length()];
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject t = arr.getJSONObject(i);
                        Transaction tx = new Transaction();
                        tx.id = t.getInt("id");
                        tx.amount = t.getLong("amount");
                        tx.description = t.optString("description", "");
                        tx.whenTime = t.optString("when_time", "");
                        tx.matchedContent = t.isNull("matched_content") ? null : t.optString("matched_content", null);
                        result[i] = tx;
                    }
                    cb.onSuccess(result);
                } else {
                    cb.onError("Lỗi server (" + code + "): " + body);
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public static class BankConfig {
        public String bankBin;
        public String accountNo;
        public String template;
    }

    public static void getConfig(Context ctx, Callback<BankConfig> cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = open(ctx, "/config", "GET");
                int code = conn.getResponseCode();
                InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String body = readAll(in);
                if (code >= 200 && code < 300) {
                    JSONObject obj = new JSONObject(body);
                    BankConfig cfg = new BankConfig();
                    cfg.bankBin = obj.getString("bank_bin");
                    cfg.accountNo = obj.getString("account_no");
                    cfg.template = obj.getString("template");
                    cb.onSuccess(cfg);
                } else {
                    cb.onError("Lỗi server (" + code + "): " + body);
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    /** Build link QR TĨNH — không addInfo, không số tiền. Người quét tự
     * nhập nội dung trong app ngân hàng của họ. */
    public static String buildStaticQrUrl(BankConfig cfg) {
        return "https://api.vietqr.io/image/" + cfg.bankBin + "-" + cfg.accountNo + "-" + cfg.template + ".jpg";
    }

    public static class Stats {
        public String period;
        public String label;
        public long total;
        public int count;
    }

    public static void getStats(Context ctx, String period, Callback<Stats> cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = open(ctx, "/stats?period=" + period, "GET");
                int code = conn.getResponseCode();
                InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                String body = readAll(in);
                if (code >= 200 && code < 300) {
                    JSONObject obj = new JSONObject(body);
                    Stats s = new Stats();
                    s.period = obj.getString("period");
                    s.label = obj.getString("label");
                    s.total = obj.getLong("total");
                    s.count = obj.getInt("count");
                    cb.onSuccess(s);
                } else {
                    cb.onError("Lỗi server (" + code + "): " + body);
                }
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }

    public static void downloadBitmap(String url, Callback<Bitmap> cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                InputStream in = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (bmp != null) cb.onSuccess(bmp);
                else cb.onError("Không giải mã được ảnh QR");
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        }).start();
    }
}
