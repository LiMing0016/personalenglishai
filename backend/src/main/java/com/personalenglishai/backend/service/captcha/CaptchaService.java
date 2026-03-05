package com.personalenglishai.backend.service.captcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 150;
    private static final int PIECE_SIZE = 40;
    private static final int TAB_R = 8;
    private static final int PIECE_IMG_WIDTH = PIECE_SIZE + TAB_R + 2;
    private static final int TOLERANCE = 4;
    private static final long CAPTCHA_EXPIRY_MS = 120_000;   // 2 min
    private static final long TOKEN_EXPIRY_MS = 60_000;      // 1 min

    private record CaptchaRecord(int correctX, long createdAt) {}
    private record TokenRecord(long createdAt) {}

    private final ConcurrentHashMap<String, CaptchaRecord> captchaCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenRecord> tokenCache = new ConcurrentHashMap<>();

    /**
     * Generate a slider captcha: background with hole + puzzle piece image.
     */
    public CaptchaResult generate() {
        cleanExpired();

        Random rng = new Random();
        int bgX = 60 + rng.nextInt(BG_WIDTH - 60 - PIECE_SIZE - TAB_R - 10);
        int bgY = 10 + rng.nextInt(BG_HEIGHT - PIECE_SIZE - 20);

        BufferedImage background = generateBackground(rng);

        // --- cut puzzle piece from background ---
        BufferedImage pieceImg = new BufferedImage(PIECE_IMG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = pieceImg.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape pieceShape = createPieceShape(1, bgY, PIECE_SIZE, TAB_R);
        pg.setClip(pieceShape);
        pg.drawImage(background, 1 - bgX, 0, null);
        pg.setClip(null);
        // border
        pg.setColor(new Color(255, 255, 255, 200));
        pg.setStroke(new BasicStroke(1.5f));
        pg.draw(pieceShape);
        pg.dispose();

        // --- draw hole on background ---
        Graphics2D bg = background.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape holeShape = createPieceShape(bgX, bgY, PIECE_SIZE, TAB_R);
        bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        bg.setColor(new Color(0, 0, 0));
        bg.fill(holeShape);
        bg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        bg.setColor(new Color(255, 255, 255, 100));
        bg.setStroke(new BasicStroke(1.5f));
        bg.draw(holeShape);
        bg.dispose();

        String captchaId = UUID.randomUUID().toString();
        captchaCache.put(captchaId, new CaptchaRecord(bgX, System.currentTimeMillis()));

        return new CaptchaResult(
                captchaId,
                "data:image/png;base64," + toBase64Png(background),
                "data:image/png;base64," + toBase64Png(pieceImg)
        );
    }

    /**
     * Verify user's slider position. Returns a one-time token on success.
     */
    public String verify(String captchaId, int userX) {
        CaptchaRecord record = captchaCache.remove(captchaId);
        if (record == null) return null;
        if (System.currentTimeMillis() - record.createdAt() > CAPTCHA_EXPIRY_MS) return null;
        if (Math.abs(record.correctX() - userX) > TOLERANCE) return null;

        String token = UUID.randomUUID().toString();
        tokenCache.put(token, new TokenRecord(System.currentTimeMillis()));
        return token;
    }

    /**
     * Validate and consume a one-time captcha token (called during login).
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        TokenRecord record = tokenCache.remove(token);
        if (record == null) return false;
        return System.currentTimeMillis() - record.createdAt() <= TOKEN_EXPIRY_MS;
    }

    // ── image generation ──

    private BufferedImage generateBackground(Random rng) {
        BufferedImage img = new BufferedImage(BG_WIDTH, BG_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // gradient background
        Color c1 = randomColor(rng, 80, 200);
        Color c2 = randomColor(rng, 80, 200);
        GradientPaint gp = new GradientPaint(0, 0, c1, BG_WIDTH, BG_HEIGHT, c2);
        g.setPaint(gp);
        g.fillRect(0, 0, BG_WIDTH, BG_HEIGHT);

        // random circles
        for (int i = 0; i < 20; i++) {
            g.setColor(new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256), 40 + rng.nextInt(80)));
            int size = 10 + rng.nextInt(50);
            g.fillOval(rng.nextInt(BG_WIDTH), rng.nextInt(BG_HEIGHT), size, size);
        }
        // random rectangles
        for (int i = 0; i < 10; i++) {
            g.setColor(new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256), 30 + rng.nextInt(60)));
            int w = 15 + rng.nextInt(50);
            int h = 10 + rng.nextInt(30);
            g.fillRoundRect(rng.nextInt(BG_WIDTH), rng.nextInt(BG_HEIGHT), w, h, 6, 6);
        }
        // random lines for texture
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256), 50 + rng.nextInt(80)));
            g.setStroke(new BasicStroke(1 + rng.nextFloat() * 2));
            g.drawLine(rng.nextInt(BG_WIDTH), rng.nextInt(BG_HEIGHT),
                       rng.nextInt(BG_WIDTH), rng.nextInt(BG_HEIGHT));
        }

        g.dispose();
        return img;
    }

    private Color randomColor(Random rng, int min, int max) {
        int range = max - min;
        return new Color(min + rng.nextInt(range), min + rng.nextInt(range), min + rng.nextInt(range));
    }

    /**
     * Square with a semicircular tab protruding on the right side.
     */
    private Shape createPieceShape(int x, int y, int size, int tabR) {
        GeneralPath path = new GeneralPath();
        double tabCenterY = y + size / 2.0;

        path.moveTo(x, y);
        path.lineTo(x + size, y);
        path.lineTo(x + size, tabCenterY - tabR);
        path.append(new Arc2D.Double(
                x + size - tabR, tabCenterY - tabR,
                tabR * 2, tabR * 2,
                90, -180, Arc2D.OPEN), true);
        path.lineTo(x + size, y + size);
        path.lineTo(x, y + size);
        path.closePath();

        return path;
    }

    private String toBase64Png(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode captcha image", e);
        }
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        captchaCache.entrySet().removeIf(e -> now - e.getValue().createdAt() > CAPTCHA_EXPIRY_MS);
        tokenCache.entrySet().removeIf(e -> now - e.getValue().createdAt() > TOKEN_EXPIRY_MS);
    }

    /** Returned by generate(). */
    public record CaptchaResult(String captchaId, String bgImage, String pieceImage) {}
}
