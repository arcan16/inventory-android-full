package com.example.myapplication.api;

/** Un inventario con conteos, del que se puede generar el reporte PDF. */
public class ReportDTO {
    private final long idInventory;
    private final String inventoryDate;
    private final String presentation;

    public ReportDTO(long idInventory, String inventoryDate, String presentation) {
        this.idInventory = idInventory;
        this.inventoryDate = inventoryDate;
        this.presentation = presentation;
    }

    public long getIdInventory() {
        return idInventory;
    }

    public String getInventoryDate() {
        return inventoryDate;
    }

    public String getPresentation() {
        return presentation;
    }
}
