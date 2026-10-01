package com.example.myapplication.api;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Operacion contra la base de datos local con la misma forma que tenia la
 * llamada de Retrofit (enqueue + Callback), para que las pantallas no cambien.
 *
 * Todas las operaciones corren en un solo hilo: se ejecutan en el orden en que
 * se piden (como lo hacia el servidor con cada peticion) y nunca dos escrituras
 * a la vez. El resultado se entrega en el hilo principal.
 */
public final class Call<T> {

    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final Callable<Response<T>> work;

    Call(@NonNull Callable<Response<T>> work) {
        this.work = work;
    }

    /** Ejecuta la operacion en el hilo actual (nunca en el principal); para pruebas y trabajo en segundo plano. */
    @NonNull
    public Response<T> execute() throws Exception {
        return work.call();
    }

    public void enqueue(@NonNull Callback<T> callback) {
        DB_EXECUTOR.execute(() -> {
            Response<T> response;
            try {
                response = work.call();
            } catch (Throwable t) {
                MAIN_HANDLER.post(() -> callback.onFailure(this, t));
                return;
            }
            MAIN_HANDLER.post(() -> callback.onResponse(this, response));
        });
    }
}
