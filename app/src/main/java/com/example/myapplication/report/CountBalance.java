package com.example.myapplication.report;

import com.example.myapplication.api.CountsDifferenceDTO;

import java.util.List;

/** Totales de sobrantes, faltantes y el balance neto del conteo fisico. */
public final class CountBalance {

    public final double totalSurplus;
    public final double totalShortage;
    public final int productsWithSurplus;
    public final int productsWithShortage;
    public final int exactProducts;

    private CountBalance(double totalSurplus, double totalShortage, int productsWithSurplus,
                         int productsWithShortage, int exactProducts) {
        this.totalSurplus = totalSurplus;
        this.totalShortage = totalShortage;
        this.productsWithSurplus = productsWithSurplus;
        this.productsWithShortage = productsWithShortage;
        this.exactProducts = exactProducts;
    }

    public static CountBalance of(List<CountsDifferenceDTO> rows) {
        double surplus = 0;
        double shortage = 0;
        int withSurplus = 0;
        int withShortage = 0;
        int exact = 0;
        for (CountsDifferenceDTO row : rows) {
            double difference = row.getDifference();
            if (difference > 0) {
                surplus += difference;
                withSurplus++;
            } else if (difference < 0) {
                shortage += -difference;
                withShortage++;
            } else {
                exact++;
            }
        }
        return new CountBalance(surplus, shortage, withSurplus, withShortage, exact);
    }

    public double netBalance() {
        return totalSurplus - totalShortage;
    }
}
