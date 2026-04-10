package com.personalenglishai.backend.common.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void shouldTreatSocketTimeoutInCauseChainAsClientAbortLike() {
        Throwable error = new RuntimeException("wrapper",
                new IllegalStateException("nested", new SocketTimeoutException("write timed out")));

        assertThat(GlobalExceptionHandler.isClientAbortLike(error)).isTrue();
    }

    @Test
    void shouldTreatBrokenPipeAsClientAbortLike() {
        Throwable error = new IOException("Broken pipe");

        assertThat(GlobalExceptionHandler.isClientAbortLike(error)).isTrue();
    }

    @Test
    void shouldNotTreatOrdinaryBusinessExceptionAsClientAbortLike() {
        Throwable error = new IllegalArgumentException("bad request");

        assertThat(GlobalExceptionHandler.isClientAbortLike(error)).isFalse();
    }
}
