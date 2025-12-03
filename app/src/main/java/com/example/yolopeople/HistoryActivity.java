package com.example.yolopeople;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * 历史记录列表页：查看、打开、删除。
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private Button btnBack;

    private List<HistoryEntry> historyList;
    private HistoryAdapter adapter;

    private HistoryStore historyStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rv_history);
        tvEmpty = findViewById(R.id.tv_empty);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        historyStore = HistoryStore.getInstance(this);
        historyList = historyStore.loadAll();

        adapter = new HistoryAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        updateEmptyView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        historyList = historyStore.loadAll();
        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (historyList == null || historyList.isEmpty()) {
            tvEmpty.setVisibility(android.view.View.VISIBLE);
            rvHistory.setVisibility(android.view.View.GONE);
        } else {
            tvEmpty.setVisibility(android.view.View.GONE);
            rvHistory.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void openEntry(HistoryEntry entry) {
        Intent intent = new Intent(this, StatsActivity.class);
        intent.putExtra(StatsActivity.EXTRA_HISTORY_ID, entry.id);
        startActivity(intent);
    }

    private void confirmDelete(HistoryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("删除历史记录")
                .setMessage("确定删除这条历史记录吗？此操作不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    historyStore.delete(entry.id);
                    historyList = historyStore.loadAll();
                    adapter.notifyDataSetChanged();
                    updateEmptyView();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ======= Adapter / ViewHolder =======

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolder> {

        @Override
        public HistoryViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater()
                    .inflate(R.layout.item_history_entry, parent, false);
            return new HistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(HistoryViewHolder holder, int position) {
            holder.bind(historyList.get(position));
        }

        @Override
        public int getItemCount() {
            return historyList == null ? 0 : historyList.size();
        }
    }

    private class HistoryViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvRemark;
        private final TextView tvDesc;
        private final Button btnOpen;
        private final Button btnDelete;

        HistoryViewHolder(android.view.View itemView) {
            super(itemView);
            tvRemark = itemView.findViewById(R.id.tv_remark);
            tvDesc = itemView.findViewById(R.id.tv_desc);
            btnOpen = itemView.findViewById(R.id.btn_open);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        void bind(HistoryEntry entry) {
            String remark = (entry.remark == null || entry.remark.isEmpty())
                    ? "未填写备注"
                    : entry.remark;
            tvRemark.setText(remark);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String timeStr = sdf.format(entry.createdAt);
            int photoCount = entry.photos == null ? 0 : entry.photos.size();
            String desc = timeStr + "  |  总人数: " + entry.totalCount
                    + "  |  图片: " + photoCount + " 张";
            tvDesc.setText(desc);

            btnOpen.setOnClickListener(v -> openEntry(entry));
            btnDelete.setOnClickListener(v -> confirmDelete(entry));
        }
    }
}

