package com.personalenglishai.backend.dto.writing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrustedRewriteSegmentDto {

    @JsonProperty("doc_id")
    private String docId;
    @JsonProperty("sentence_text")
    private String sentenceText;
    @JsonProperty("normalized_text_hash")
    private String normalizedTextHash;
    @JsonProperty("left_context")
    private String leftContext;
    @JsonProperty("right_context")
    private String rightContext;
    private String tier;
    private String source;
    @JsonProperty("updated_at")
    private Long updatedAt;

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getSentenceText() {
        return sentenceText;
    }

    public void setSentenceText(String sentenceText) {
        this.sentenceText = sentenceText;
    }

    public String getNormalizedTextHash() {
        return normalizedTextHash;
    }

    public void setNormalizedTextHash(String normalizedTextHash) {
        this.normalizedTextHash = normalizedTextHash;
    }

    public String getLeftContext() {
        return leftContext;
    }

    public void setLeftContext(String leftContext) {
        this.leftContext = leftContext;
    }

    public String getRightContext() {
        return rightContext;
    }

    public void setRightContext(String rightContext) {
        this.rightContext = rightContext;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
