package com.example.myapplication.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

public class PageResponseTest {

    @Test
    public void computesTotalPagesLikeSpringData() {
        assertEquals(0, new PageResponse<>(Collections.emptyList(), 20, 0).getTotalPages());
        assertEquals(1, new PageResponse<>(Collections.emptyList(), 20, 20).getTotalPages());
        assertEquals(2, new PageResponse<>(Collections.emptyList(), 20, 21).getTotalPages());
    }
}
