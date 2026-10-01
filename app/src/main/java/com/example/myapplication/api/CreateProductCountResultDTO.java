package com.example.myapplication.api;

/** Resultado de ProductCountsApi.createProductAddCount: el producto dado de alta y su conteo. */
public class CreateProductCountResultDTO {
    private final NewProduct newProduct;
    private final CountCreated countCreated;

    public CreateProductCountResultDTO(NewProduct newProduct, CountCreated countCreated) {
        this.newProduct = newProduct;
        this.countCreated = countCreated;
    }

    public NewProduct getNewProduct() {
        return newProduct;
    }

    public CountCreated getCountCreated() {
        return countCreated;
    }

    public static class NewProduct {
        private final String id;
        private final String description;

        public NewProduct(String id, String description) {
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

    public static class CountCreated {
        private final long id;
        private final float quantity;
        private final String place;

        public CountCreated(long id, float quantity, String place) {
            this.id = id;
            this.quantity = quantity;
            this.place = place;
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
    }
}
