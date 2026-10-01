package com.example.myapplication.api;

/** Un inventario de la lista (tabla inventories). */
public class InventoryDTO {
    private final long id;
    private final String date;
    private final String presentation;
    private final String status;

    public InventoryDTO(long id, String date, String presentation, String status) {
        this.id = id;
        this.date = date;
        this.presentation = presentation;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    /** "yyyy-MM-dd". */
    public String getDate() {
        return date;
    }

    public String getPresentation() {
        return presentation;
    }

    /** OPENED, PENDING o CLOSED. */
    public String getStatus() {
        return status;
    }
}
