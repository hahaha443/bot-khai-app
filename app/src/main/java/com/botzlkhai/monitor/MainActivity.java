package com.botzlkhai.monitor;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    // Đổi URL này sang trang dashboard/index.html đã upload lên host
    private static final String DASHBOARD_URL = "https://zrmteam.x10.mx/app-bot-zeplo/dashboard/index.html";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(webParams);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setWebViewClient(new WebViewClient());

        root.addView(webView);

        // Nút refresh nổi góc dưới phải (thay cho pull-to-refresh)
        Button refreshBtn = new Button(this);
        refreshBtn.setText("↻");
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.BOTTOM | Gravity.END;
        btnParams.setMargins(0, 0, 24, 24);
        refreshBtn.setLayoutParams(btnParams);
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.reload();
            }
        });
        root.addView(refreshBtn);

        setContentView(root);
        webView.loadUrl(DASHBOARD_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
