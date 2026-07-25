package com.personalenglishai.backend.common.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    @Test
    void handleBiz_mapsVocabularyImageGatewayErrorsByNumericPrefix() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var outputInvalid = handler.handleBiz(new BizException(ErrorCode.VOCABULARY_IMAGE_OUTPUT_INVALID));
        var timeout = handler.handleBiz(new BizException(ErrorCode.VOCABULARY_IMAGE_TIMEOUT));

        assertThat(outputInvalid.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(outputInvalid.getBody().getCode()).isEqualTo("502050");
        assertThat(timeout.getBody().getCode()).isEqualTo("504050");
    }

    @Test
    void handleMissingServletRequestPart_mapsMissingPartToStableHttp400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleMissingServletRequestPart(
                new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("400001");
        assertThat(response.getBody().getMessage()).isEqualTo("参数验证失败");
    }

    @Test
    void handleIllegalArgument_mapsClientInputToHttp400WithoutCatchingInfrastructureFailures() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var invalidInput = handler.handleIllegalArgument(new IllegalArgumentException("unsupported template"));
        var infrastructure = handler.handleOther(new IllegalStateException("database unavailable"));

        assertThat(invalidInput.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidInput.getBody().getCode()).isEqualTo(ErrorCode.COMMON_VALIDATION_ERROR.getCode());
        assertThat(infrastructure.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void unreadableRequestLogDoesNotExposeRawJacksonMessageOrRequestContent() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            var response = handler.handleHttpMessageNotReadable(
                    new HttpMessageNotReadableException("raw body contains secret vocabulary"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getMessage()).isEqualTo("请求体格式错误");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allMatch(message -> !message.contains("secret vocabulary"))
                    .allMatch(message -> !message.contains("raw body"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }
}
