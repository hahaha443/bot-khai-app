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
                    // Mới nhất lên trên - render card rõ nét theo chuẩn Dark Modern
                    for (int i = result.length - 1; i >= 0; i--) {
                        ApiClient.Transaction t = result[i];

                        LinearLayout card = new LinearLayout(TransactionHistoryActivity.this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setBackgroundResource(R.drawable.bg_section_card);
                        int p = dpToPx(12);
                        card.setPadding(p, p, p, p);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lp.bottomMargin = dpToPx(10);
                        card.setLayoutParams(lp);

                        // Hàng trên: Số tiền (xanh lá nổi bật) + Thời gian (bên phải)
                        LinearLayout topRow = new LinearLayout(TransactionHistoryActivity.this);
                        topRow.setOrientation(LinearLayout.HORIZONTAL);
                        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        TextView amountTv = new TextView(TransactionHistoryActivity.this);
                        amountTv.setText(String.format("+%,d đ", t.amount));
                        amountTv.setTextColor(0xFF10B981);
                        amountTv.setTextSize(16);
                        amountTv.setTypeface(null, android.graphics.Typeface.BOLD);
                        amountTv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                        TextView timeTv = new TextView(TransactionHistoryActivity.this);
                        timeTv.setText(t.whenTime != null ? t.whenTime : "");
                        timeTv.setTextColor(0xFF94A3B8);
                        timeTv.setTextSize(12);

                        topRow.addView(amountTv);
                        topRow.addView(timeTv);
                        card.addView(topRow);

                        // Hàng dưới: Nội dung chuyển khoản rõ nét
                        if (t.description != null && !t.description.trim().isEmpty()) {
                            TextView descTv = new TextView(TransactionHistoryActivity.this);
                            descTv.setText(t.description);
                            descTv.setTextColor(0xFFF1F5F9);
                            descTv.setTextSize(13);
                            descTv.setBackgroundResource(R.drawable.bg_stat_chip);
                            int descPad = dpToPx(8);
                            descTv.setPadding(descPad, descPad, descPad, descPad);
                            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            descLp.topMargin = dpToPx(8);
                            descTv.setLayoutParams(descLp);
                            card.addView(descTv);
                        }

                        list.addView(card);
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

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
