package com.example.myapplication;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.api.ApiClient;
import com.example.myapplication.api.ApiErrorUtils;
import com.example.myapplication.api.InventoryUploadApi;
import com.example.myapplication.api.InventoryUploadResultDTO;
import com.example.myapplication.util.FileUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.myapplication.api.Call;
import com.example.myapplication.api.Callback;
import com.example.myapplication.api.Response;

public class InventoryUploadActivity extends BaseActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private Uri selectedFileUri;

    private TextView tvFileName;
    private TextView tvFileMeta;
    private MaterialButton btnUpload;
    private View progressUpload;

    private final ActivityResultLauncher<String> filePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_upload);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvFileName = findViewById(R.id.tvFileName);
        tvFileMeta = findViewById(R.id.tvFileMeta);
        btnUpload = findViewById(R.id.btnUpload);
        progressUpload = findViewById(R.id.progressUpload);

        findViewById(R.id.dropzone).setOnClickListener(v -> filePicker.launch("*/*"));
        btnUpload.setOnClickListener(v -> uploadSelectedFile());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }

        String name = FileUtils.getDisplayName(getContentResolver(), uri);
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            Toast.makeText(this, R.string.upload_error_not_csv, Toast.LENGTH_SHORT).show();
            return;
        }

        selectedFileUri = uri;

        long size = FileUtils.getSize(getContentResolver(), uri);
        tvFileName.setText(name);
        tvFileMeta.setText(getString(R.string.upload_dropzone_selected_meta, FileUtils.formatFileSize(size)));

        btnUpload.setEnabled(true);
    }

    private void uploadSelectedFile() {
        if (selectedFileUri == null) {
            return;
        }

        setLoadingState(true);

        Uri fileUri = selectedFileUri;

        ioExecutor.execute(() -> {
            byte[] bytes;
            try {
                bytes = FileUtils.readAllBytes(getContentResolver(), fileUri);
            } catch (IOException e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runOnUiThread(() -> {
                    setLoadingState(false);
                    Toast.makeText(this, getString(R.string.upload_error_read_file, reason), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            InventoryUploadApi api = ApiClient.createInventoryUploadApi();

            api.upload(bytes).enqueue(new Callback<InventoryUploadResultDTO>() {
                @Override
                public void onResponse(@NonNull Call<InventoryUploadResultDTO> call,
                                        @NonNull Response<InventoryUploadResultDTO> response) {
                    setLoadingState(false);
                    handleUploadResponse(response);
                }

                @Override
                public void onFailure(@NonNull Call<InventoryUploadResultDTO> call, @NonNull Throwable t) {
                    setLoadingState(false);
                    String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                    Toast.makeText(InventoryUploadActivity.this,
                            getString(R.string.upload_error_generic, reason), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handleUploadResponse(Response<InventoryUploadResultDTO> response) {
        if (response.isSuccessful() && response.body() != null) {
            // Regresa a la lista de inventarios (que se recarga con RESULT_OK) mostrando el
            // resumen de la carga. Salir tambien evita volver a subir el mismo archivo por accidente.
            InventoryUploadResultDTO result = response.body();
            String summary = getString(R.string.upload_result_title, result.getInventoryId()) + "\n"
                    + getString(R.string.upload_result_body,
                    result.getRowsProcessed(), result.getPresentation(),
                    result.getProductsCreated(), result.getStockRowsCreated());
            Toast.makeText(getApplicationContext(), summary, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.upload_error_generic, ApiErrorUtils.parseErrorMessage(response)), Toast.LENGTH_LONG).show();
        }
    }

    private void setLoadingState(boolean loading) {
        btnUpload.setEnabled(!loading && selectedFileUri != null);
        btnUpload.setText(loading ? R.string.upload_submit_loading : R.string.upload_submit);
        progressUpload.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.dropzone).setEnabled(!loading);
    }
}
