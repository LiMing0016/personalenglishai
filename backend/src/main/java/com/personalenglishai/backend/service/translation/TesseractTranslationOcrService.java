package com.personalenglishai.backend.service.translation;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "tesseract", matchIfMissing = true)
public class TesseractTranslationOcrService implements TranslationOcrService {
    private final String tesseractPath;
    private final String language;
    private final int dpi;
    private final Duration timeout;

    public TesseractTranslationOcrService(
            @Value("${app.ocr.tesseract-path:tesseract}") String tesseractPath,
            @Value("${app.ocr.language:eng}") String language,
            @Value("${app.ocr.dpi:220}") int dpi,
            @Value("${app.ocr.timeout-seconds:45}") long timeoutSeconds) {
        this.tesseractPath = tesseractPath;
        this.language = language;
        this.dpi = dpi;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public TranslationOcrResult recognizePdf(byte[] pdfBytes) {
        Path tempDir = null;
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            tempDir = Files.createTempDirectory("translation-ocr-");
            PDFRenderer renderer = new PDFRenderer(document);
            List<TranslationOcrPageText> pages = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                Path imageFile = tempDir.resolve("page-" + (pageIndex + 1) + ".png");
                ImageIO.write(image, "png", imageFile.toFile());
                String text = runTesseract(imageFile);
                if (text != null && !text.isBlank()) {
                    pages.add(new TranslationOcrPageText(pageIndex + 1, text));
                }
            }
            if (pages.isEmpty()) {
                return TranslationOcrResult.failed("OCR 未识别到有效文本");
            }
            return TranslationOcrResult.succeeded(pages);
        } catch (IOException e) {
            return TranslationOcrResult.unavailable("OCR 引擎不可用或 PDF 渲染失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TranslationOcrResult.failed("OCR 识别被中断");
        } finally {
            deleteTempDir(tempDir);
        }
    }

    private String runTesseract(Path imageFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                tesseractPath,
                imageFile.toString(),
                "stdout",
                "-l",
                language,
                "--psm",
                "6"
        ).redirectErrorStream(true).start();

        boolean completed = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("OCR timeout");
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        if (process.exitValue() != 0) {
            throw new IOException("OCR command failed: " + output.toString().strip());
        }
        return output.toString();
    }

    private void deleteTempDir(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try (var files = Files.walk(tempDir)) {
            files.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Temp OCR files are best-effort cleanup.
                        }
                    });
        } catch (IOException ignored) {
            // Temp OCR files are best-effort cleanup.
        }
    }
}
