package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.StockSummaryAdapter;
import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.StockApi;
import com.example.myapplication.api.StockItemDTO;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.example.myapplication.api.Call;
import com.example.myapplication.api.Callback;
import com.example.myapplication.api.Response;

public class StockSummaryActivity extends BaseActivity {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_TYPE_LABEL = "extra_type_label";

    private StockSummaryAdapter adapter;

    private long inventoryId;
    private List<StockItemDTO> allItems = Collections.emptyList();
    private String currentFilter = "";

    private EditText etFilter;
    private View progressLoad;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_summary);

        inventoryId = getIntent().getLongExtra(EXTRA_INVENTORY_ID, -1);
        String typeLabel = getIntent().getStringExtra(EXTRA_TYPE_LABEL);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(typeLabel != null ? typeLabel + " · #" + inventoryId : "#" + inventoryId);

        etFilter = findViewById(R.id.etFilter);
        etFilter.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        progressLoad = findViewById(R.id.progressLoad);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        RecyclerView recyclerStock = findViewById(R.id.recyclerStock);

        adapter = new StockSummaryAdapter();
        recyclerStock.setLayoutManager(new LinearLayoutManager(this));
        recyclerStock.setAdapter(adapter);

        findViewById(R.id.btnRetry).setOnClickListener(v -> loadStock());

        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentFilter = s.toString().trim();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        loadStock();
    }

    private void loadStock() {
        if (inventoryId <= 0) {
            showError(getString(R.string.stock_summary_load_error));
            return;
        }

        emptyStateContainer.setVisibility(View.GONE);
        progressLoad.setVisibility(View.VISIBLE);

        StockApi api = ApiClient.createStockApi();

        api.getStock(inventoryId).enqueue(new Callback<List<StockItemDTO>>() {
            @Override
            public void onResponse(@NonNull Call<List<StockItemDTO>> call, @NonNull Response<List<StockItemDTO>> response) {
                progressLoad.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    allItems = response.body();
                    applyFilter();
                } else {
                    showError(getString(R.string.stock_summary_load_error));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<StockItemDTO>> call, @NonNull Throwable t) {
                progressLoad.setVisibility(View.GONE);
                showError(getString(R.string.stock_summary_load_error));
            }
        });
    }

    /** Filtra allItems por descripcion (sin distinguir mayusculas), igual que SummaryStock.jsx en el frontend web. */
    private void applyFilter() {
        if (currentFilter.isEmpty()) {
            adapter.setItems(allItems);
        } else {
            List<StockItemDTO> filtered = new ArrayList<>();
            String needle = currentFilter.toLowerCase(Locale.getDefault());
            for (StockItemDTO item : allItems) {
                String description = item.getDescription();
                if (description != null && description.toLowerCase(Locale.getDefault()).contains(needle)) {
                    filtered.add(item);
                }
            }
            adapter.setItems(filtered);
        }

        if (adapter.isEmpty()) {
            tvEmptyMessage.setText(currentFilter.isEmpty()
                    ? R.string.stock_summary_empty
                    : R.string.stock_summary_filter_empty);
            emptyStateContainer.setVisibility(View.VISIBLE);
        } else {
            emptyStateContainer.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        allItems = Collections.emptyList();
        adapter.setItems(Collections.emptyList());
        tvEmptyMessage.setText(message);
        emptyStateContainer.setVisibility(View.VISIBLE);
    }
}
