package com.hihu.donatefloat;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class TransactionHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        LinearLayout list = findViewById(R.id.historyList);
        findViewById(R.id.btnRefreshHistory).setOnClickListener(v -> load(list));
        load(list);
    }

    private void load(LinearLayout list) {
        ApiClient.getTransactions(this, 0, 200, new ApiClient.Callback<ApiClient.Transaction[]>() {
            @Override
            public void onSuccess(ApiClient.Transaction[] result) {
                runOnUiThread(() -> {
                    list.removeAllViews();
                    if (result.length == 0) {
                        TextView empty = new TextView(TransactionHistoryActivity.this);
                        empty.setText("Chưa có giao dịch nào.");
                        empty.setPadding(16, 16, 16, 16);
                        list.addView(empty);
                        return;
                    }
                    // Mới nhất lên trên
                    for (int i = result.length - 1; i >= 0; i--) {
                        ApiClient.Transaction t = result[i];
                        TextView tv = new TextView(TransactionHistoryActivity.this);
                        tv.setText(String.format("+%,d đ  •  %s\n%s", t.amount, t.whenTime, t.description));
                        tv.setPadding(16, 16, 16, 16);
                        tv.setTextSize(13);
                        list.addView(tv);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(TransactionHistoryActivity.this,
                        "Lỗi tải lịch sử: " + message, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
