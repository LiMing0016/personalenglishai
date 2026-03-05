package com.personalenglishai.backend.controller.auth.dto;

public class CaptchaResponse {
    private String captchaId;
    private String bgImage;
    private String pieceImage;

    public CaptchaResponse(String captchaId, String bgImage, String pieceImage) {
        this.captchaId = captchaId;
        this.bgImage = bgImage;
        this.pieceImage = pieceImage;
    }

    public String getCaptchaId() { return captchaId; }
    public String getBgImage() { return bgImage; }
    public String getPieceImage() { return pieceImage; }
}
