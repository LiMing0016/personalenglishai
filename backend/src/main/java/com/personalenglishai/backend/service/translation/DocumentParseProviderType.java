package com.personalenglishai.backend.service.translation;

public enum DocumentParseProviderType {
    LOCAL_PDFBOX("local-pdfbox"),
    THIRD_PARTY_LAYOUT("third-party-layout"),
    PADDLE_OCR("paddle-ocr"),
    CLOUD_DOCUMENT_AI("cloud-document-ai"),
    SELF_DEVELOPED("self-developed");

    private final String wireName;

    DocumentParseProviderType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
