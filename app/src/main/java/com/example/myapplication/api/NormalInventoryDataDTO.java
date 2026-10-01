package com.example.myapplication.api;

import java.util.List;

/**
 * Todo lo que necesita un conteo al abrirse: catalogo de productos, conteos ya
 * registrados (del mas viejo al mas nuevo), stock esperado y las presentaciones
 * que tienen codigo de barras (para resolver un escaneo sin otra consulta).
 */
public class NormalInventoryDataDTO {
    private final List<ProductDTO> products;
    private final List<ProductCountEntryDTO> productsCount;
    private final List<StockItemDTO> stock;
    private final List<ProductPresentationDTO> barcodes;

    public NormalInventoryDataDTO(List<ProductDTO> products, List<ProductCountEntryDTO> productsCount,
                                  List<StockItemDTO> stock, List<ProductPresentationDTO> barcodes) {
        this.products = products;
        this.productsCount = productsCount;
        this.stock = stock;
        this.barcodes = barcodes;
    }

    public List<ProductDTO> getProducts() {
        return products;
    }

    public List<ProductCountEntryDTO> getProductsCount() {
        return productsCount;
    }

    public List<StockItemDTO> getStock() {
        return stock;
    }

    public List<ProductPresentationDTO> getBarcodes() {
        return barcodes;
    }
}
