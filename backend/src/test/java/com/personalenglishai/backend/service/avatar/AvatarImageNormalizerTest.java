package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageNormalizerTest {

    private final AvatarImageNormalizer normalizer = new AvatarImageNormalizer();

    @Test
    void normalizesLargeJpegToBoundedPng() throws IOException {
        byte[] jpeg = createImage("jpg", 1800, 900);
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", jpeg);

        AvatarImageNormalizer.NormalizedAvatar result = normalizer.normalize(file);

        assertThat(result.bytes()).startsWith(
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
        assertThat(result.width()).isEqualTo(1024);
        assertThat(result.height()).isEqualTo(512);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(1024);
        assertThat(decoded.getHeight()).isEqualTo(512);
    }

    @Test
    void keepsSmallPngDimensionsWhileReencoding() throws IOException {
        byte[] png = createImage("png", 320, 240);
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png", png);

        AvatarImageNormalizer.NormalizedAvatar result = normalizer.normalize(file);

        assertThat(result.width()).isEqualTo(320);
        assertThat(result.height()).isEqualTo(240);
        assertThat(ImageIO.read(new ByteArrayInputStream(result.bytes()))).isNotNull();
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        assertAvatarInvalid(file, "头像文件不能为空");
    }

    @Test
    void rejectsFileLargerThanFiveMiB() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "oversized.png", "image/png", oversized);

        assertAvatarInvalid(file, "头像不能超过 5MB");
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchActualFormat() throws IOException {
        byte[] png = createImage("png", 64, 64);
        MockMultipartFile file = new MockMultipartFile(
                "file", "spoofed.jpg", "image/jpeg", png);

        assertAvatarInvalid(file, "图片格式与文件内容不一致");
    }

    @Test
    void rejectsDirectWebpOrUnknownContent() {
        byte[] webpHeader = new byte[] {
                'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.webp", "image/webp", webpHeader);

        assertAvatarInvalid(file, "仅支持 JPG 或 PNG 图片");
    }

    @Test
    void rejectsCorruptImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "broken.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a});

        assertAvatarInvalid(file, "图片无法解析");
    }

    @Test
    void rejectsImageDimensionsAboveLimitBeforeFullDecode() throws IOException {
        byte[] png = createImage("png", 4097, 1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "too-wide.png", "image/png", png);

        assertAvatarInvalid(file, "图片尺寸过大");
    }

    private void assertAvatarInvalid(MockMultipartFile file, String message) {
        assertThatThrownBy(() -> normalizer.normalize(file))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode())
                        .isEqualTo(ErrorCode.USER_AVATAR_INVALID))
                .hasMessage(message);
    }

    private byte[] createImage(String format, int width, int height) throws IOException {
        int imageType = "jpg".equals(format)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, imageType);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(12, 133, 104));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }
}
