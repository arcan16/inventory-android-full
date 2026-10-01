package com.example.myapplication.domain;

/**
 * Presentaciones de un producto. El "label" es el valor exacto guardado en
 * product_presentations.presentation (y el mismo texto que llega en la columna
 * UNIDAD de los CSV de inventario, p. ej. "4 LTS"). El orden de las constantes
 * es el de menor a mayor volumen y se usa para ordenar las presentaciones.
 */
public enum ProductPresentation {
    EIGHTH_LITER("1/8 LT"),
    QUARTER_LITER("1/4 LT"),
    ONE_LITER("1 LT"),
    GALLON("3.785 LTS"),
    FOUR_LITERS("4 LTS"),
    TWO_AND_A_HALF_GALLONS("9.46 LTS"),
    NINETEEN_LITERS("19 LTS"),
    PIECE("PIEZA");

    private final String label;

    ProductPresentation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Busca la presentacion por su label ignorando mayusculas y espacios sobrantes; null si no existe. */
    public static ProductPresentation findByLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim().replaceAll("\\s+", " ");
        for (ProductPresentation presentation : values()) {
            if (presentation.label.equalsIgnoreCase(normalized)) {
                return presentation;
            }
        }
        return null;
    }
}
