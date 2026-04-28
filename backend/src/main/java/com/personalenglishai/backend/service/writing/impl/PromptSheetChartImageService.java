package com.personalenglishai.backend.service.writing.impl;

import com.personalenglishai.backend.dto.writing.GenerateExamPromptResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class PromptSheetChartImageService {

    private static final int WIDTH = 1100;
    private static final int HEIGHT = 720;
    private static final Color TEXT = new Color(15, 23, 42);
    private static final Color MUTED = new Color(71, 85, 105);
    private static final Color GRID = new Color(226, 232, 240);
    private static final Color AXIS = new Color(148, 163, 184);
    private static final Color BAR = new Color(65, 112, 184);
    private static final Color LINE = new Color(234, 121, 46);

    private final Path uploadRoot;
    private final String publicUploadPath;

    @Autowired
    public PromptSheetChartImageService(
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.upload-public-path:/uploads}") String publicUploadPath) {
        this(Paths.get(uploadDir), publicUploadPath);
    }

    PromptSheetChartImageService(Path uploadRoot, String publicUploadPath) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
        this.publicUploadPath = normalizePublicPath(publicUploadPath);
    }

    public void attachChartImage(GenerateExamPromptResponse response) {
        if (!shouldRender(response)) {
            return;
        }

        try {
            Path chartDir = uploadRoot.resolve("prompt-sheets").resolve("charts");
            Files.createDirectories(chartDir);
            String filename = hashChart(response.getChartSpec()) + ".png";
            Path imagePath = chartDir.resolve(filename);
            if (Files.notExists(imagePath)) {
                BufferedImage image = renderChart(response);
                ImageIO.write(image, "png", imagePath.toFile());
            }
            response.setAttachmentType("visual");
            response.setVisualKind("image");
            response.setAttachmentImageUrl(publicUploadPath + "/prompt-sheets/charts/" + filename);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render prompt sheet chart image", e);
        }
    }

    private boolean shouldRender(GenerateExamPromptResponse response) {
        if (response == null || response.getChartSpec() == null) {
            return false;
        }
        if (hasText(response.getAttachmentImageUrl())) {
            return false;
        }
        String displayType = normalize(response.getChartSpec().getDisplayType());
        return !"table".equals(displayType)
                && response.getChartSpec().getColumns().size() >= 2
                && response.getChartSpec().getRows().size() >= 2;
    }

    private BufferedImage renderChart(GenerateExamPromptResponse response) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            GenerateExamPromptResponse.ChartSpec chartSpec = response.getChartSpec();
            drawTitle(g, firstNonBlank(chartSpec.getTitle(), response.getAttachmentTitle(), response.getTopic()));
            ChartData chartData = toChartData(chartSpec);
            drawPlot(g, chartData);
            drawLegend(g, chartData);
            drawSummary(g, chartSpec.getSummary());
        } finally {
            g.dispose();
        }
        return image;
    }

    private void drawTitle(Graphics2D g, String title) {
        g.setColor(TEXT);
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        drawCentered(g, title, 55);
    }

    private void drawPlot(Graphics2D g, ChartData data) {
        int left = 120;
        int top = 105;
        int width = 835;
        int height = 420;
        int bottom = top + height;
        int right = left + width;

        g.setStroke(new BasicStroke(1.3f));
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        for (int i = 0; i <= 5; i++) {
            int y = top + Math.round(height * i / 5f);
            g.setColor(GRID);
            g.drawLine(left, y, right, y);
        }
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(left, top, left, bottom);
        g.drawLine(left, bottom, right, bottom);
        if (data.hasRightAxis()) {
            g.drawLine(right, top, right, bottom);
        }

        drawAxisLabels(g, data, left, top, right);
        drawBarsAndLines(g, data, left, top, width, height);
        drawXLabels(g, data, left, bottom, width);
    }

    private void drawAxisLabels(Graphics2D g, ChartData data, int left, int top, int right) {
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.setColor(MUTED);
        g.drawString(data.leftAxisLabel(), left - 5, top - 22);
        if (data.hasRightAxis()) {
            FontMetrics fm = g.getFontMetrics();
            String label = data.rightAxisLabel();
            g.drawString(label, right - fm.stringWidth(label), top - 22);
        }
    }

    private void drawBarsAndLines(Graphics2D g, ChartData data, int left, int top, int width, int height) {
        List<String> labels = data.labels();
        float step = labels.size() <= 1 ? width : width / (float) (labels.size() - 1);
        int barWidth = Math.max(26, Math.min(48, Math.round(step * 0.42f)));
        int bottom = top + height;

        for (Series series : data.series()) {
            if (series.kind() != SeriesKind.BAR) {
                continue;
            }
            g.setColor(BAR);
            for (int i = 0; i < series.values().size(); i++) {
                int x = Math.round(left + step * i) - barWidth / 2;
                int y = yFor(series.values().get(i), series.range(), top, height);
                g.fillRoundRect(x, y, barWidth, bottom - y, 8, 8);
                drawPointLabel(g, series.rawValues().get(i), x + barWidth / 2, y - 10, BAR);
            }
        }

        for (Series series : data.series()) {
            if (series.kind() != SeriesKind.LINE) {
                continue;
            }
            g.setColor(LINE);
            g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int previousX = -1;
            int previousY = -1;
            for (int i = 0; i < series.values().size(); i++) {
                int x = Math.round(left + step * i);
                int y = yFor(series.values().get(i), series.range(), top, height);
                if (previousX >= 0) {
                    g.drawLine(previousX, previousY, x, y);
                }
                g.fillOval(x - 6, y - 6, 12, 12);
                drawPointLabel(g, series.rawValues().get(i), x, y - 14, LINE);
                previousX = x;
                previousY = y;
            }
        }
    }

    private void drawXLabels(Graphics2D g, ChartData data, int left, int bottom, int width) {
        List<String> labels = data.labels();
        float step = labels.size() <= 1 ? width : width / (float) (labels.size() - 1);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.setColor(MUTED);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < labels.size(); i++) {
            int x = Math.round(left + step * i);
            String label = labels.get(i);
            g.drawString(label, x - fm.stringWidth(label) / 2, bottom + 42);
        }
    }

    private void drawLegend(Graphics2D g, ChartData data) {
        int x = 120;
        int y = 620;
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        for (Series series : data.series()) {
            g.setColor(series.kind() == SeriesKind.BAR ? BAR : LINE);
            if (series.kind() == SeriesKind.BAR) {
                g.fillRoundRect(x, y - 18, 30, 20, 5, 5);
            } else {
                g.setStroke(new BasicStroke(4.0f));
                g.drawLine(x, y - 8, x + 30, y - 8);
                g.fillOval(x + 11, y - 15, 14, 14);
            }
            g.setColor(MUTED);
            g.drawString(series.name(), x + 42, y);
            x += Math.max(230, g.getFontMetrics().stringWidth(series.name()) + 85);
        }
    }

    private void drawSummary(Graphics2D g, String summary) {
        if (!hasText(summary)) {
            return;
        }
        g.setColor(MUTED);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        drawCentered(g, summary.trim(), 675);
    }

    private void drawPointLabel(Graphics2D g, String label, int x, int y, Color color) {
        if (!hasText(label)) {
            return;
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(label);
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRoundRect(x - textWidth / 2 - 5, y - 18, textWidth + 10, 22, 8, 8);
        g.setColor(color.darker());
        g.drawString(label, x - textWidth / 2, y);
    }

    private void drawCentered(Graphics2D g, String text, int y) {
        String safeText = hasText(text) ? text.trim() : "Chart";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(safeText, (WIDTH - fm.stringWidth(safeText)) / 2, y);
    }

    private int yFor(double value, Range range, int top, int height) {
        double denominator = range.max() - range.min();
        double normalized = denominator == 0 ? 0.5d : (value - range.min()) / denominator;
        return top + height - (int) Math.round(normalized * height);
    }

    private ChartData toChartData(GenerateExamPromptResponse.ChartSpec chartSpec) {
        List<String> columns = chartSpec.getColumns();
        List<List<String>> rows = chartSpec.getRows();
        List<String> labels = rows.stream()
                .map(row -> row.isEmpty() ? "" : firstNonBlank(row.get(0), ""))
                .toList();
        List<SeriesDraft> drafts = new ArrayList<>();
        for (int columnIndex = 1; columnIndex < columns.size(); columnIndex++) {
            List<Double> values = new ArrayList<>();
            List<String> rawValues = new ArrayList<>();
            for (List<String> row : rows) {
                String rawValue = row.size() > columnIndex ? row.get(columnIndex) : "";
                Double numericValue = parseNumber(rawValue);
                if (numericValue == null) {
                    numericValue = 0d;
                }
                values.add(numericValue);
                rawValues.add(firstNonBlank(rawValue, String.valueOf(numericValue)));
            }
            String name = firstNonBlank(columns.get(columnIndex), "Series " + columnIndex);
            drafts.add(new SeriesDraft(name, values, rawValues, isRateSeries(name)));
        }

        boolean hasRate = drafts.stream().anyMatch(SeriesDraft::rightAxis);
        boolean hasValue = drafts.stream().anyMatch(draft -> !draft.rightAxis());
        List<Double> leftValues = drafts.stream()
                .filter(draft -> !draft.rightAxis())
                .flatMap(draft -> draft.values().stream())
                .toList();
        List<Double> rightValues = drafts.stream()
                .filter(SeriesDraft::rightAxis)
                .flatMap(draft -> draft.values().stream())
                .toList();
        Range leftRange = rangeFor(leftValues);
        Range rightRange = rangeFor(rightValues);

        List<Series> series = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            SeriesDraft draft = drafts.get(i);
            SeriesKind kind = hasRate && hasValue && !draft.rightAxis() && i == 0 ? SeriesKind.BAR : SeriesKind.LINE;
            series.add(new Series(
                    draft.name(),
                    draft.values(),
                    draft.rawValues(),
                    kind,
                    draft.rightAxis() ? rightRange : leftRange
            ));
        }

        return new ChartData(
                labels,
                series,
                drafts.stream().filter(draft -> !draft.rightAxis()).map(SeriesDraft::name).findFirst().orElse(columns.get(1)),
                drafts.stream().filter(SeriesDraft::rightAxis).map(SeriesDraft::name).findFirst().orElse(null)
        );
    }

    private Range rangeFor(List<Double> values) {
        if (values.isEmpty()) {
            return new Range(0, 1);
        }
        double minValue = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double maxValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        double min = minValue > 0 ? 0 : minValue;
        double max = maxValue == min ? maxValue + 1 : maxValue;
        return new Range(min, max);
    }

    private Double parseNumber(String value) {
        if (!hasText(value)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("-?\\d+(?:\\.\\d+)?")
                .matcher(value.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group());
    }

    private boolean isRateSeries(String name) {
        String lower = normalize(name);
        return lower.contains("%")
                || lower.contains("rate")
                || lower.contains("ratio")
                || lower.contains("growth")
                || lower.contains("增速")
                || lower.contains("增长率")
                || lower.contains("比例")
                || lower.contains("率");
    }

    private String hashChart(GenerateExamPromptResponse.ChartSpec chartSpec) {
        StringBuilder builder = new StringBuilder();
        builder.append(chartSpec.getTitle()).append('|')
                .append(chartSpec.getDisplayType()).append('|')
                .append(String.join(",", chartSpec.getColumns())).append('|');
        for (List<String> row : chartSpec.getRows()) {
            builder.append(String.join(",", row)).append('|');
        }
        builder.append(chartSpec.getSummary());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalizePublicPath(String value) {
        String normalized = hasText(value) ? value.trim() : "/uploads";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record ChartData(List<String> labels, List<Series> series, String leftAxisLabel, String rightAxisLabel) {
        boolean hasRightAxis() {
            return hasText(rightAxisLabel);
        }
    }

    private record Series(String name, List<Double> values, List<String> rawValues, SeriesKind kind, Range range) {
    }

    private record SeriesDraft(String name, List<Double> values, List<String> rawValues, boolean rightAxis) {
    }

    private record Range(double min, double max) {
    }

    private enum SeriesKind {
        BAR,
        LINE
    }
}
