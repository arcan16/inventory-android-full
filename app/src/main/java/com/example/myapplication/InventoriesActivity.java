package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.InventoryAdapter;
import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.ApiErrorUtils;
import com.example.myapplication.api.InventoriesApi;
import com.example.myapplication.api.InventoryDTO;
import com.example.myapplication.api.PageResponse;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.example.myapplication.api.Call;
import com.example.myapplication.api.Callback;
import com.example.myapplication.api.Response;

public class InventoriesActivity extends BaseActivity implements InventoryAdapter.OnInventoryActionListener {

    private static final int PAGE_SIZE = 50;

    /**
     * Abre las pantallas que cambian la lista: conteo normal y guiado (que puede
     * cerrar el inventario con Finalizar conteo) y carga de archivo (inventario
     * nuevo). Al regresar se recarga la lista para mostrar el estado actual.
     */
    private final ActivityResultLauncher<Intent> reloadOnResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> loadInventories(false));

    private InventoryAdapter adapter;

    private SwipeRefreshLayout swipeRefresh;
    private View progressInventories;
    private View emptyStateContainer;
    private TextView tvEmptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventories);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressInventories = findViewById(R.id.progressInventories);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        RecyclerView recyclerView = findViewById(R.id.recyclerInventories);
        FloatingActionButton fab = findViewById(R.id.fabAddInventory);

        adapter = new InventoryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> loadInventories(false));
        findViewById(R.id.btnRetry).setOnClickListener(v -> loadInventories(true));
        // Al cargar un archivo, InventoryUploadActivity responde RESULT_OK y la lista se recarga.
        fab.setOnClickListener(v -> reloadOnResultLauncher.launch(new Intent(this, InventoryUploadActivity.class)));

        loadInventories(true);
    }

    private void loadInventories(boolean showFullProgress) {
        hideEmptyState();
        if (showFullProgress) {
            progressInventories.setVisibility(View.VISIBLE);
        }

        InventoriesApi api = ApiClient.createInventoriesApi();

        api.getInventories(0, PAGE_SIZE).enqueue(new Callback<PageResponse<InventoryDTO>>() {
            @Override
            public void onResponse(@NonNull Call<PageResponse<InventoryDTO>> call,
                                    @NonNull Response<PageResponse<InventoryDTO>> response) {
                progressInventories.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null) {
                    adapter.setItems(response.body().getContent());
                    if (adapter.isEmpty()) {
                        showEmptyState(getString(R.string.inventories_empty));
                    }
                } else {
                    showEmptyState(getString(R.string.inventories_load_error));
                }
            }

            @Override
            public void onFailure(@NonNull Call<PageResponse<InventoryDTO>> call, @NonNull Throwable t) {
                progressInventories.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showEmptyState(getString(R.string.inventories_load_error));
                Toast.makeText(InventoriesActivity.this,
                        t.getMessage() != null ? t.getMessage() : getString(R.string.inventories_load_error),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmptyState(String message) {
        tvEmptyMessage.setText(message);
        emptyStateContainer.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        emptyStateContainer.setVisibility(View.GONE);
    }

    @Override
    public void onOpenNormalCount(InventoryDTO inventory) {
        if ("CLOSED".equals(inventory.getStatus())) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.inventories_closed_open_title)
                    .setMessage(getString(R.string.inventories_closed_open_message, inventory.getPresentation()))
                    .setPositiveButton(R.string.inventories_reopen_confirm, (dialog, which) -> reopenInventory(inventory))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
            return;
        }

        openNormalCount(inventory);
    }

    private void openNormalCount(InventoryDTO inventory) {
        Intent intent = new Intent(this, CountNormalActivity.class);
        intent.putExtra(CountNormalActivity.EXTRA_INVENTORY_ID, inventory.getId());
        intent.putExtra(CountNormalActivity.EXTRA_PRESENTATION, inventory.getPresentation());
        intent.putExtra(CountNormalActivity.EXTRA_DATE, inventory.getDate());
        reloadOnResultLauncher.launch(intent);
    }

    private void reopenInventory(InventoryDTO inventory) {
        InventoriesApi api = ApiClient.createInventoriesApi();

        api.reopenInventory(inventory.getId()).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(InventoriesActivity.this, R.string.inventories_reopened_toast, Toast.LENGTH_SHORT).show();
                    loadInventories(false);
                    openNormalCount(inventory);
                } else {
                    Toast.makeText(InventoriesActivity.this,
                            getString(R.string.inventories_reopen_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(InventoriesActivity.this,
                        getString(R.string.inventories_reopen_error, reason),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onOpenGuidedCount(InventoryDTO inventory) {
        Intent intent = new Intent(this, CountGuidedActivity.class);
        intent.putExtra(CountGuidedActivity.EXTRA_INVENTORY_ID, inventory.getId());
        intent.putExtra(CountGuidedActivity.EXTRA_PRESENTATION, inventory.getPresentation());
        intent.putExtra(CountGuidedActivity.EXTRA_DATE, inventory.getDate());
        reloadOnResultLauncher.launch(intent);
    }

    @Override
    public void onClose(InventoryDTO inventory) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.inventories_close_confirm_title)
                .setMessage(getString(R.string.inventories_close_confirm_message, inventory.getPresentation()))
                .setPositiveButton(R.string.inventories_menu_close, (dialog, which) -> closeInventory(inventory))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void closeInventory(InventoryDTO inventory) {
        InventoriesApi api = ApiClient.createInventoriesApi();

        api.closeInventory(inventory.getId()).enqueue(new Callback<InventoryDTO>() {
            @Override
            public void onResponse(@NonNull Call<InventoryDTO> call, @NonNull Response<InventoryDTO> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(InventoriesActivity.this, R.string.inventories_closed_toast, Toast.LENGTH_SHORT).show();
                    loadInventories(false);
                } else {
                    Toast.makeText(InventoriesActivity.this,
                            getString(R.string.inventories_close_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<InventoryDTO> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(InventoriesActivity.this,
                        getString(R.string.inventories_close_error, reason),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDelete(InventoryDTO inventory) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.inventories_delete_confirm_title)
                .setMessage(getString(R.string.inventories_delete_confirm_message, inventory.getPresentation()))
                .setPositiveButton(R.string.inventories_menu_delete, (dialog, which) -> deleteInventory(inventory))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteInventory(InventoryDTO inventory) {
        InventoriesApi api = ApiClient.createInventoriesApi();

        api.deleteInventory(inventory.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    adapter.removeItem(inventory);
                    Toast.makeText(InventoriesActivity.this, R.string.inventories_deleted_toast, Toast.LENGTH_SHORT).show();
                    if (adapter.isEmpty()) {
                        showEmptyState(getString(R.string.inventories_empty));
                    }
                } else {
                    Toast.makeText(InventoriesActivity.this,
                            getString(R.string.inventories_delete_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(InventoriesActivity.this,
                        getString(R.string.inventories_delete_error, reason),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
