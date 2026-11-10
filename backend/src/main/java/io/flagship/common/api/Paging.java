package io.flagship.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class Paging {

    public static final int MAX_PAGE_SIZE = 100;

    private Paging() {
    }

    /** Builds a Pageable, clamping page >= 0 and 1 <= size <= MAX_PAGE_SIZE. */
    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
