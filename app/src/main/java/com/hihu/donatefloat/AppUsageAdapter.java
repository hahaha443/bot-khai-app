package com.hihu.donatefloat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {

    public interface OnForceStopClick {
        void onForceStop(RamCpuChecker.AppUsage app);
    }

    private final List<RamCpuChecker.AppUsage> items;
    private final OnForceStopClick listener;

    public AppUsageAdapter(List<RamCpuChecker.AppUsage> items, OnForceStopClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RamCpuChecker.AppUsage app = items.get(position);

        holder.label.setText(app.label);
        holder.pkg.setText(app.packageName);
        holder.stats.setText(String.format(Locale.getDefault(),
                "RAM: %,d KB   •   CPU: %.1f%%", app.pssKb, app.cpuPercent));

        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon);
        } else {
            holder.icon.setImageResource(R.drawable.ic_launcher);
        }

        // App hệ thống lõi: vẫn cho buộc dừng nhưng cảnh báo rõ trong dialog xác nhận
        holder.btnForceStop.setOnClickListener(v -> {
            if (listener != null) listener.onForceStop(app);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView label, pkg, stats;
        Button btnForceStop;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgAppIcon);
            label = itemView.findViewById(R.id.textAppLabel);
            pkg = itemView.findViewById(R.id.textAppPackage);
            stats = itemView.findViewById(R.id.textAppStats);
            btnForceStop = itemView.findViewById(R.id.btnForceStop);
        }
    }
}
