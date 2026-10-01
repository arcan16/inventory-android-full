package com.example.myapplication.api;

import androidx.annotation.NonNull;

/** Resultado de un Call; ambos metodos se llaman en el hilo principal. */
public interface Callback<T> {

    /** La operacion termino: exito o un error de validacion (ver Response.isSuccessful). */
    void onResponse(@NonNull Call<T> call, @NonNull Response<T> response);

    /** Error inesperado (p. ej. de SQLite o de escritura del PDF). */
    void onFailure(@NonNull Call<T> call, @NonNull Throwable t);
}
