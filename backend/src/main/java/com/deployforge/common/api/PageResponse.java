package com.deployforge.common.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Offset pagination envelope used for projects, deployments, notifications and activity. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }

    public static <T> PageResponse<T> of(List<T> items) {
        return new PageResponse<>(items, 0, items.size(), items.size(), 1, false, false);
    }
}
