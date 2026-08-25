package com.deployforge.common.api;

import java.util.List;

/**
 * Cursor pagination envelope, used for deployment logs.
 *
 * <p>Logs are append only and identified by a monotonically increasing sequence number per
 * deployment, which makes {@code afterSequence} a stable cursor even while new lines stream in.
 */
public record CursorPageResponse<T>(
        List<T> items, Long nextCursor, boolean hasMore, int limit) {

    public static <T> CursorPageResponse<T> of(List<T> items, Long nextCursor, boolean hasMore, int limit) {
        return new CursorPageResponse<>(items, nextCursor, hasMore, limit);
    }
}
