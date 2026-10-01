package com.example.myapplication.api;

import androidx.annotation.Nullable;

/**
 * Resultado de una operacion local. Conserva los codigos que usaba el backend
 * (200 exito, 400 validacion, 404 no encontrado) porque las pantallas ya
 * distinguen esos casos.
 */
public final class Response<T> {

    public static final int OK = 200;
    public static final int BAD_REQUEST = 400;
    public static final int NOT_FOUND = 404;

    private final int code;
    private final T body;
    private final String errorMessage;

    private Response(int code, T body, String errorMessage) {
        this.code = code;
        this.body = body;
        this.errorMessage = errorMessage;
    }

    public static <T> Response<T> success(@Nullable T body) {
        return new Response<>(OK, body, null);
    }

    public static <T> Response<T> badRequest(String errorMessage) {
        return new Response<>(BAD_REQUEST, null, errorMessage);
    }

    public static <T> Response<T> notFound(String errorMessage) {
        return new Response<>(NOT_FOUND, null, errorMessage);
    }

    public boolean isSuccessful() {
        return code == OK;
    }

    public int code() {
        return code;
    }

    @Nullable
    public T body() {
        return body;
    }

    /** Mensaje para el usuario cuando no es exitosa; null en exito. */
    @Nullable
    public String errorMessage() {
        return errorMessage;
    }
}
