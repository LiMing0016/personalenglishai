package com.personalenglishai.backend.service.dictionary;

public class DictionaryLookupException extends RuntimeException {
    public enum Kind {
        INVALID_CONFIG,
        NOT_FOUND,
        FORBIDDEN,
        QUOTA_EXCEEDED,
        UPSTREAM_ERROR,
        TIMEOUT,
        RESPONSE_INVALID
    }

    private final Kind kind;

    public DictionaryLookupException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public DictionaryLookupException(Kind kind, Throwable cause) {
        super(kind.name(), cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
