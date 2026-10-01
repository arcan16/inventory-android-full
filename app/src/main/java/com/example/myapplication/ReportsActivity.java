package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.ReportAdapter;
import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.ApiErrorUtils;
import com.example.myapplication.api.ProductCountsApi;
import com.example.myapplication.api.ReportDTO;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.example.myapplication.api.Call;
import com.example.myapplication.api.Callback;
import com.example.myapplication.api.Response;

public class ReportsActivity extends BaseActivity implements ReportAdapter.OnReportClickListener {

    private ReportAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private View progressLoad;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressLoad = findViewById(R.id.progressLoad);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        RecyclerView recyclerReports = findViewById(R.id.recyclerReports);

        adapter = new ReportAdapter(this);
        recyclerReports.setLayoutManager(new LinearLayoutManager(this));
        recyclerReports.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> loadReports(false));
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadReports(true));

        loadReports(true);
    }

    private void loadReports(boolean showFullProgress) {
        emptyStateContainer.setVisibility(View.GONE);
        if (showFullProgress) {
            progressLoad.setVisibility(View.VISIBLE);
        }

        ProductCountsApi api = ApiClient.createProductCountsApi();

        api.getAllReports().enqueue(new Callback<List<ReportDTO>>() {
            @Override
            public void onResponse(@NonNull Call<List<ReportDTO>> call, @NonNull Response<List<ReportDTO>> response) {
                progressLoad.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<ReportDTO> reports = new ArrayList<>(response.body());
                    reports.sort(Comparator.comparing(ReportDTO::getInventoryDate).reversed());
                    adapter.setItems(reports);
                    tvEmptyMessage.setText(R.string.reports_empty);
                    emptyStateContainer.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    showLoadError();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ReportDTO>> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showLoadError();
            }
        });
    }

    private void showLoadError() {
        adapter.setItems(Collections.emptyList());
        tvEmptyMessage.setText(R.string.reports_load_error);
        emptyStateContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDownloadReport(ReportDTO report) {
        adapter.setDownloadingInventoryId(report.getIdInventory());

        ProductCountsApi api = ApiClient.createProductCountsApi();

        api.downloadReport(report.getIdInventory()).enqueue(new Callback<File>() {
            @Override
            public void onResponse(@NonNull Call<File> call, @NonNull Response<File> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setDownloadingInventoryId(-1);
                    openPdf(response.body());
                } else {
                    adapter.setDownloadingInventoryId(-1);
                    Toast.makeText(ReportsActivity.this,
                            getString(R.string.reports_download_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<File> call, @NonNull Throwable t) {
                adapter.setDownloadingInventoryId(-1);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(ReportsActivity.this,
                        getString(R.string.reports_download_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openPdf(File pdfFile) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.reports_download_open_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.reports_download_no_viewer, Toast.LENGTH_LONG).show();
        }
    }
}
