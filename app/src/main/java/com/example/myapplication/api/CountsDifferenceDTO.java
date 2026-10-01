package com.example.myapplication.api;

/**
 * Renglon del resumen de un conteo: stock esperado contra lo contado. id es
 * el renglon de stock; 0 para productos contados que no venian en el stock.
 */
public class CountsDifferenceDTO {
    private final long id;
    private final String description;
    private final String idProduct;
    private final float stock;
    private final double sum;
    private final double difference;

    public CountsDifferenceDTO(long id, String description, String idProduct, float stock, double sum, double difference) {
        this.id = id;
        this.description = description;
        this.idProduct = idProduct;
        this.stock = stock;
        this.sum = sum;
        this.difference = difference;
    }

    public long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getIdProduct() {
        return idProduct;
    }

    public float getStock() {
        return stock;
    }

    public double getSum() {
        return sum;
    }

    public double getDifference() {
        return difference;
    }
}
