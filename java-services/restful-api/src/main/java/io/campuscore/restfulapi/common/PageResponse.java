package io.campuscore.restfulapi.common;

import java.util.List;

/**
 * Standard paginated collection envelope for list operations.
 *
 * @param <T> Element type
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 1;
        boolean last = pageNumber >= totalPages;
        return new PageResponse<>(content, pageNumber, pageSize, totalElements, totalPages, last);
    }
}
