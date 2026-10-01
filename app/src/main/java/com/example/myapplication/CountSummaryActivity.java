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

import com.example.myapplication.adapter.CountSummaryAdapter;
import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.ApiErrorUtils;
import com.example.myapplication.api.CountsDifferenceDTO;
import com.example.myapplication.api.PageResponse;
import com.example.myapplication.api.ProductCountsApi;
import com.example.myapplication.util.DateFormatUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.Collections;

import com.example.myapplication.api.Call;
import com.example.myapplication.api.Callback;
import com.example.myapplication.api.Response;

public class CountSummaryActivity extends BaseActivity {

    public static final String EXTRA_INVENTORY_ID = "extra_inventory_id";
    public static final String EXTRA_PRESENTATION = "extra_presentation";
    public static final String EXTRA_DATE = "extra_date";

    private static final int PAGE_SIZE = 20;
    /** Cuantos renglones antes del final se dispara la siguiente pagina. */
    private static final int LOAD_MORE_THRESHOLD = 4;

    private long inventoryId;
    private int currentPage = 0;
    private int totalPages = 1;
    private boolean isLoadingPage;
    /** Pestaña Diferencias: solo productos cuyo conteo no coincide con el stock. */
    private boolean onlyDifferences;
    /** Descarta respuestas de la pestaña anterior si el usuario cambia de pestaña mientras carga. */
    private int requestGeneration;
    private TabLayout tabsSummary;

    private View contentContainer;
    private View progressLoad;
    private View progressLoadMore;
    private View errorState;
    private TextView tvErrorMessage;
    private RecyclerView recyclerSummary;
    private TextView tvSummaryEmpty;
    private CountSummaryAdapter adapter;
    private MaterialButton btnExport;
    private View progressExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_count_summary);

        inventoryId = getIntent().getLongExtra(EXTRA_INVENTORY_ID, -1);
        String presentation = getIntent().getStringExtra(EXTRA_PRESENTATION);
        String dateIso = getIntent().getStringExtra(EXTRA_DATE);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(presentation + " · " + DateFormatUtils.toShortSpanishDate(dateIso));

        contentContainer = findViewById(R.id.contentContainer);
        progressLoad = findViewById(R.id.progressLoad);
        progressLoadMore = findViewById(R.id.progressLoadMore);
        errorState = findViewById(R.id.errorState);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        recyclerSummary = findViewById(R.id.recyclerSummary);
        tvSummaryEmpty = findViewById(R.id.tvSummaryEmpty);
        btnExport = findViewById(R.id.btnExport);
        progressExport = findViewById(R.id.progressExport);

        adapter = new CountSummaryAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerSummary.setLayoutManager(layoutManager);
        recyclerSummary.setAdapter(adapter);
        recyclerSummary.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - LOAD_MORE_THRESHOLD) {
                    loadNextPage();
                }
            }
        });

        tabsSummary = findViewById(R.id.tabsSummary);
        tabsSummary.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                onlyDifferences = tab.getPosition() == 1;
                adapter.setItems(Collections.emptyList());
                currentPage = 0;
                totalPages = 1;
                loadSummary();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                recyclerSummary.scrollToPosition(0);
            }
        });

        findViewById(R.id.btnRetryLoad).setOnClickListener(v -> loadSummary());
        btnExport.setOnClickListener(v -> exportPdf());

        loadSummary();
    }

    private void loadSummary() {
        if (inventoryId <= 0) {
            showError(getString(R.string.count_summary_load_error));
            return;
        }

        fetchPage(0, false);
    }

    private void loadNextPage() {
        if (isLoadingPage || currentPage + 1 >= totalPages) {
            return;
        }
        fetchPage(currentPage + 1, true);
    }

    private void fetchPage(int page, boolean appending) {
        int generation = ++requestGeneration;
        boolean differencesTab = onlyDifferences;
        isLoadingPage = true;
        if (appending) {
            progressLoadMore.setVisibility(View.VISIBLE);
        } else {
            progressLoad.setVisibility(View.VISIBLE);
            errorState.setVisibility(View.GONE);
            contentContainer.setVisibility(View.GONE);
        }

        ProductCountsApi api = ApiClient.createProductCountsApi();

        api.getSummary(inventoryId, page, PAGE_SIZE, differencesTab).enqueue(new Callback<PageResponse<CountsDifferenceDTO>>() {
            @Override
            public void onResponse(@NonNull Call<PageResponse<CountsDifferenceDTO>> call,
                                    @NonNull Response<PageResponse<CountsDifferenceDTO>> response) {
                if (generation != requestGeneration) {
                    return;
                }
                isLoadingPage = false;
                progressLoad.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    currentPage = page;
                    totalPages = response.body().getTotalPages();
                    if (appending) {
                        adapter.addItems(response.body().getContent());
                    } else {
                        adapter.setItems(response.body().getContent());
                        recyclerSummary.scrollToPosition(0);
                        if (differencesTab) {
                            setDifferencesTabCount(response.body().getTotalElements());
                        }
                    }
                    tvSummaryEmpty.setText(differencesTab
                            ? R.string.count_summary_no_differences : R.string.count_summary_empty);
                    tvSummaryEmpty.setVisibility(adapter.isEmpty() ? View.VISIBLE : View.GONE);
                    contentContainer.setVisibility(View.VISIBLE);
                } else if (!appending && response.code() == 400) {
                    // ProductCountsApi responde 400 cuando el inventario todavia no tiene stock ni conteos.
                    adapter.setItems(Collections.emptyList());
                    totalPages = 0;
                    tvSummaryEmpty.setText(R.string.count_summary_empty);
                    tvSummaryEmpty.setVisibility(View.VISIBLE);
                    contentContainer.setVisibility(View.VISIBLE);
                } else if (!appending) {
                    showError(getString(R.string.count_summary_load_error));
                } else {
                    Toast.makeText(CountSummaryActivity.this, R.string.count_summary_load_more_error, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<PageResponse<CountsDifferenceDTO>> call, @NonNull Throwable t) {
                if (generation != requestGeneration) {
                    return;
                }
                isLoadingPage = false;
                progressLoad.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);
                if (appending) {
                    Toast.makeText(CountSummaryActivity.this, R.string.count_summary_load_more_error, Toast.LENGTH_SHORT).show();
                } else {
                    showError(getString(R.string.count_summary_load_error));
                }
            }
        });
    }

    /** "Diferencias (12)": cuantos productos no coinciden con el stock. */
    private void setDifferencesTabCount(long count) {
        TabLayout.Tab tab = tabsSummary.getTabAt(1);
        if (tab != null) {
            tab.setText(getString(R.string.count_summary_tab_differences_count, (int) count));
        }
    }

    private void showError(String message) {
        tvErrorMessage.setText(message);
        errorState.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);
    }

    private void exportPdf() {
        if (inventoryId <= 0) {
            return;
        }

        setExportLoadingState(true);

        ProductCountsApi api = ApiClient.createProductCountsApi();

        api.downloadReport(inventoryId).enqueue(new Callback<File>() {
            @Override
            public void onResponse(@NonNull Call<File> call, @NonNull Response<File> response) {
                if (response.isSuccessful() && response.body() != null) {
                    setExportLoadingState(false);
                    openPdf(response.body());
                } else {
                    setExportLoadingState(false);
                    Toast.makeText(CountSummaryActivity.this,
                            getString(R.string.count_summary_export_error, ApiErrorUtils.parseErrorMessage(response)),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<File> call, @NonNull Throwable t) {
                setExportLoadingState(false);
                String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Toast.makeText(CountSummaryActivity.this,
                        getString(R.string.count_summary_export_error, reason), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openPdf(File pdfFile) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.count_summary_export_open_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.count_summary_export_no_viewer, Toast.LENGTH_LONG).show();
        }
    }

    private void setExportLoadingState(boolean loading) {
        btnExport.setEnabled(!loading);
        btnExport.setText(loading ? R.string.count_summary_export_loading : R.string.count_summary_export);
        progressExport.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
