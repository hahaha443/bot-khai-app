package com.hihu.donatefloat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.ViewHolder> {

    public interface OnForceStopClick {
        void onForceStop(RamCpuChecker.AppUsage app);
    }

    public interface OnSelectionChanged {
        void onSelectionChanged(Set<String> selectedPackages);
    }

    private final List<RamCpuChecker.AppUsage> items;
    private final OnForceStopClick stopListener;
    private final OnSelectionChanged selectionListener;
    private final Set<String> selected = new HashSet<>();

    public AppUsageAdapter(List<RamCpuChecker.AppUsage> items,
                            OnForceStopClick stopListener,
                            OnSelectionChanged selectionListener) {
        this.items = items;
        this.stopListener = stopListener;
        this.selectionListener = selectionListener;
    }

    /** Gọi khi danh sách bị lọc/làm mới, để bỏ chọn những package không còn hiển thị nữa. */
    public void pruneSelection() {
        Set<String> visible = new HashSet<>();
        for (RamCpuChecker.AppUsage a : items) visible.add(a.packageName);
        selected.retainAll(visible);
        if (selectionListener != null) selectionListener.onSelectionChanged(selected);
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
        if (selectionListener != null) selectionListener.onSelectionChanged(selected);
    }

    public Set<String> getSelected() {
        return selected;
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

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selected.contains(app.packageName));
        holder.checkBox.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) selected.add(app.packageName);
            else selected.remove(app.packageName);
            if (selectionListener != null) selectionListener.onSelectionChanged(selected);
        });

        holder.btnForceStop.setOnClickListener(v -> {
            if (stopListener != null) stopListener.onForceStop(app);
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
        CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgAppIcon);
            label = itemView.findViewById(R.id.textAppLabel);
            pkg = itemView.findViewById(R.id.textAppPackage);
            stats = itemView.findViewById(R.id.textAppStats);
            btnForceStop = itemView.findViewById(R.id.btnForceStop);
            checkBox = itemView.findViewById(R.id.checkSelect);
        }
    }
}
