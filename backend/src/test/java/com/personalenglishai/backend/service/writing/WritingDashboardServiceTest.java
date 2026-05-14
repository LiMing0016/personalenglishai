package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.mapper.DocumentScoreSummaryMapper;
import com.personalenglishai.backend.mapper.EssayEvaluationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WritingDashboardServiceTest {

    @Mock
    private DocumentScoreSummaryMapper documentScoreSummaryMapper;

    @Mock
    private EssayEvaluationMapper essayEvaluationMapper;

    @Test
    void buildDashboard_returnsStableEmptyShape() {
        WritingDashboardService service = new WritingDashboardService(documentScoreSummaryMapper, essayEvaluationMapper);
        when(documentScoreSummaryMapper.selectDashboardLatestRows(eq(1L), eq("all"), any(), any())).thenReturn(List.of());
        when(essayEvaluationMapper.selectDashboardSubmissionRows(eq(1L), eq("all"), any(), any())).thenReturn(List.of());

        Map<String, Object> response = service.buildDashboard(1L, "30d", "all", null, null);

        Map<?, ?> overview = (Map<?, ?>) response.get("overview");
        Map<?, ?> summary = (Map<?, ?>) overview.get("summary");
        Map<?, ?> growth = (Map<?, ?>) response.get("growth");
        assertEquals(0, summary.get("totalEssays"));
        assertEquals(0, summary.get("totalSubmissions"));
        assertEquals(List.of(), overview.get("trend"));
        assertEquals(5, ((List<?>) growth.get("scoreDistribution")).size());
        assertTrue(String.valueOf(overview.get("insight")).contains("先完成一篇作文评分"));
    }

    @Test
    void buildDashboard_bucketsScoresAndBuildsLowHighScoreInsight() {
        WritingDashboardService service = new WritingDashboardService(documentScoreSummaryMapper, essayEvaluationMapper);
        List<Map<String, Object>> latestRows = List.of(
                latest("Essay 1", "free", 55, LocalDateTime.of(2026, 5, 1, 20, 0)),
                latest("Essay 2", "exam", 65, LocalDateTime.of(2026, 5, 2, 20, 0)),
                latest("Essay 3", "free", 72, LocalDateTime.of(2026, 5, 3, 20, 0)),
                latest("Essay 4", "exam", 76, LocalDateTime.of(2026, 5, 4, 20, 0))
        );
        List<Map<String, Object>> submissions = List.of(
                submission(55, LocalDateTime.of(2026, 5, 1, 20, 0)),
                submission(65, LocalDateTime.of(2026, 5, 2, 20, 0)),
                submission(72, LocalDateTime.of(2026, 5, 3, 20, 0)),
                submission(76, LocalDateTime.of(2026, 5, 4, 20, 0))
        );
        when(documentScoreSummaryMapper.selectDashboardLatestRows(eq(1L), eq("all"), any(), any())).thenReturn(latestRows);
        when(essayEvaluationMapper.selectDashboardSubmissionRows(eq(1L), eq("all"), any(), any())).thenReturn(submissions);

        Map<String, Object> response = service.buildDashboard(1L, "30d", "all", null, null);

        Map<?, ?> overview = (Map<?, ?>) response.get("overview");
        Map<?, ?> summary = (Map<?, ?>) overview.get("summary");
        Map<?, ?> growth = (Map<?, ?>) response.get("growth");
        List<?> distribution = (List<?>) growth.get("scoreDistribution");

        assertEquals(4, summary.get("totalEssays"));
        assertEquals(4, summary.get("totalSubmissions"));
        assertEquals(67, summary.get("averageScore"));
        assertEquals(76, summary.get("bestScore"));
        assertEquals(0, growth.get("highScorePercent"));
        assertTrue(String.valueOf(growth.get("insight")).contains("80 分以上占比"));
        assertEquals(1L, ((Map<?, ?>) distribution.get(0)).get("count"));
        assertEquals(1L, ((Map<?, ?>) distribution.get(1)).get("count"));
        assertEquals(2L, ((Map<?, ?>) distribution.get(2)).get("count"));
    }

    private Map<String, Object> latest(String title, String mode, int score, LocalDateTime at) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("mode", mode);
        row.put("latestOverallScore", score);
        row.put("bestOverallScore", score);
        row.put("latestEvaluationAt", at);
        return row;
    }

    private Map<String, Object> submission(int score, LocalDateTime at) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("overallScore", score);
        row.put("evaluatedAt", at);
        return row;
    }
}
