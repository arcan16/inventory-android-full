package com.example.myapplication.report;

import static org.junit.Assert.assertEquals;

import com.example.myapplication.api.CountsDifferenceDTO;

import org.junit.Test;

import java.util.Arrays;

public class CountBalanceTest {

    @Test
    public void sumsSurplusShortageAndExactProducts() {
        CountBalance balance = CountBalance.of(Arrays.asList(
                row(2.5),
                row(-1.25),
                row(-0.5),
                row(0)));

        assertEquals(1, balance.productsWithSurplus);
        assertEquals(2, balance.productsWithShortage);
        assertEquals(1, balance.exactProducts);
        assertEquals(2.5, balance.totalSurplus, 1e-9);
        assertEquals(1.75, balance.totalShortage, 1e-9);
        assertEquals(0.75, balance.netBalance(), 1e-9);
    }

    private static CountsDifferenceDTO row(double difference) {
        return new CountsDifferenceDTO(1, "PRODUCTO", "1", 0f, difference, difference);
    }
}
