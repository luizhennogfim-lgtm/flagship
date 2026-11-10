package io.flagship.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestIdFilterTest {

    @Test
    void safeValuesAreKept() {
        assertThat(RequestIdFilter.sanitize("abc-123_X.y")).isEqualTo("abc-123_X.y");
    }

    @Test
    void missingValuesAreRejected() {
        assertThat(RequestIdFilter.sanitize(null)).isNull();
        assertThat(RequestIdFilter.sanitize("")).isNull();
    }

    @Test
    void valuesThatCouldForgeLogLinesAreRejected() {
        assertThat(RequestIdFilter.sanitize("abc\nERROR fake line")).isNull();
        assertThat(RequestIdFilter.sanitize("has space")).isNull();
        assertThat(RequestIdFilter.sanitize("<script>")).isNull();
    }

    @Test
    void tooLongValuesAreRejected() {
        assertThat(RequestIdFilter.sanitize("a".repeat(64))).isNotNull();
        assertThat(RequestIdFilter.sanitize("a".repeat(65))).isNull();
    }
}
