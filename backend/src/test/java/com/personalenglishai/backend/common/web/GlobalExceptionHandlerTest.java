package com.personalenglishai.backend.common.web;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

    @Test
    void handleBiz_mapsCommonSystemErrorToHttp500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBiz(new BizException(ErrorCode.COMMON_SYSTEM_ERROR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
