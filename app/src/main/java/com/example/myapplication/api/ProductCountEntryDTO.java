package com.example.myapplication.api;

/** Un conteo ya registrado de un inventario, con el producto al que pertenece. */
public class ProductCountEntryDTO {
    private final long id;
    private final float quantity;
    private final String place;
    private final ProductRef idProduct;

    public ProductCountEntryDTO(long id, float quantity, String place, ProductRef idProduct) {
        this.id = id;
        this.quantity = quantity;
        this.place = place;
        this.idProduct = idProduct;
    }

    public long getId() {
        return id;
    }

    public float getQuantity() {
        return quantity;
    }

    public String getPlace() {
        return place;
    }

    public ProductRef getIdProduct() {
        return idProduct;
    }

    public static class ProductRef {
        private final String id;
        private final String description;

        public ProductRef(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }
}
