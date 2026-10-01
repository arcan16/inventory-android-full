package com.example.myapplication.api;

/** Texto de error para mostrar al usuario cuando una operacion no fue exitosa. */
public final class ApiErrorUtils {

    private ApiErrorUtils() {
    }

    public static String parseErrorMessage(Response<?> response) {
        if (response.errorMessage() != null) {
            return response.errorMessage();
        }
        return "Error " + response.code();
    }
}
