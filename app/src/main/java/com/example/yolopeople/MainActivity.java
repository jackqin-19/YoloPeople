package com.example.yolopeople;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button btnStartDetect;
    private Button btnHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartDetect = findViewById(R.id.btn_start_detect);
        btnHistory = findViewById(R.id.btn_history);

        btnStartDetect.setOnClickListener(v -> {
            Intent intent = new Intent(this, StatsActivity.class);
            startActivity(intent);
        });

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });
    }
}
