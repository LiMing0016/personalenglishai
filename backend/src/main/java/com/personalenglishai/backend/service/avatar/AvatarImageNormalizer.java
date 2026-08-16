package com.personalenglishai.backend.service.avatar;

import com.personalenglishai.backend.common.error.BizException;
import com.personalenglishai.backend.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Component
public class AvatarImageNormalizer {

    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final int MAX_SOURCE_EDGE = 4096;
    static final long MAX_SOURCE_PIXELS = 16_777_216L;
    static final int MAX_OUTPUT_EDGE = 1024;

    private static final Map<String, String> FORMAT_MIME_TYPES = Map.of(
            "jpeg", "image/jpeg",
            "png", "image/png"
    );

    public NormalizedAvatar normalize(MultipartFile file) {
        validateFileEnvelope(file);

        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) {
                throw invalid("图片无法解析");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                if (!FORMAT_MIME_TYPES.containsValue(normalizeMime(file.getContentType()))) {
                    throw invalid("仅支持 JPG 或 PNG 图片");
                }
                throw invalid("图片无法解析");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = normalizeFormat(reader.getFormatName());
                String expectedMime = FORMAT_MIME_TYPES.get(format);
                if (expectedMime == null) {
                    throw invalid("仅支持 JPG 或 PNG 图片");
                }
                if (!expectedMime.equals(normalizeMime(file.getContentType()))) {
                    throw invalid("图片格式与文件内容不一致");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalid("图片无法解析");
                }
                return encodeNormalizedPng(decoded, width, height);
            } finally {
                reader.dispose();
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("图片无法解析");
        }
    }

    private void validateFileEnvelope(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("头像文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw invalid("头像不能超过 5MB");
        }

        String mime = normalizeMime(file.getContentType());
        if (!FORMAT_MIME_TYPES.containsValue(mime)) {
            throw invalid("仅支持 JPG 或 PNG 图片");
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0
                || width > MAX_SOURCE_EDGE
                || height > MAX_SOURCE_EDGE
                || (long) width * height > MAX_SOURCE_PIXELS) {
            throw invalid("图片尺寸过大");
        }
    }

    private NormalizedAvatar encodeNormalizedPng(BufferedImage source, int sourceWidth, int sourceHeight)
            throws IOException {
        double scale = Math.min(1D, (double) MAX_OUTPUT_EDGE / Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage normalized = new BufferedImage(
                targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(normalized, "png", output)) {
            throw invalid("图片无法解析");
        }
        return new NormalizedAvatar(output.toByteArray(), targetWidth, targetHeight);
    }

    private String normalizeFormat(String format) {
        String normalized = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return "jpg".equals(normalized) ? "jpeg" : normalized;
    }

    private String normalizeMime(String mime) {
        return mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.USER_AVATAR_INVALID, message);
    }

    public record NormalizedAvatar(byte[] bytes, int width, int height) {
    }
}
