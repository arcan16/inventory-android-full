package com.example.myapplication.api;

import java.util.List;

/** Una pagina de resultados (misma forma que el Page<T> de Spring Data que consumia la app). */
public class PageResponse<T> {
    private final List<T> content;
    private final int totalPages;
    private final long totalElements;

    public PageResponse(List<T> content, int pageSize, long totalElements) {
        this.content = content;
        this.totalPages = pageSize <= 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);
        this.totalElements = totalElements;
    }

    public List<T> getContent() {
        return content;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }
}
