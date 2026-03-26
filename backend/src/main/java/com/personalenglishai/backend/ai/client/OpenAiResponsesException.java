package com.personalenglishai.backend.ai.client;

public class OpenAiResponsesException extends RuntimeException {

    private final Integer statusCode;
    private final String errorCode;
    private final String errorParam;
    private final String responseBody;

    public OpenAiResponsesException(Integer statusCode,
                                    String errorCode,
                                    String errorParam,
                                    String responseBody,
                                    Throwable cause) {
        super(buildMessage(statusCode, errorCode, errorParam), cause);
        this.statusCode = statusCode;
        this.errorCode = normalize(errorCode);
        this.errorParam = normalize(errorParam);
        this.responseBody = responseBody == null ? "" : responseBody;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorParam() {
        return errorParam;
    }

    public String getResponseBody() {
        return responseBody;
    }

    private static String buildMessage(Integer statusCode, String errorCode, String errorParam) {
        StringBuilder sb = new StringBuilder("OpenAI responses request failed");
        if (statusCode != null) {
            sb.append(" status=").append(statusCode);
        }
        String normalizedCode = normalize(errorCode);
        if (!normalizedCode.isEmpty()) {
            sb.append(" code=").append(normalizedCode);
        }
        String normalizedParam = normalize(errorParam);
        if (!normalizedParam.isEmpty()) {
            sb.append(" param=").append(normalizedParam);
        }
        return sb.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
