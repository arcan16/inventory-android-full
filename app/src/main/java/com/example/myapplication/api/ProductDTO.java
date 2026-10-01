package com.example.myapplication.api;

/** Un producto del catalogo. */
public class ProductDTO {
    private String id;
    private String description;

    public ProductDTO() {
    }

    public ProductDTO(String id, String description) {
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
