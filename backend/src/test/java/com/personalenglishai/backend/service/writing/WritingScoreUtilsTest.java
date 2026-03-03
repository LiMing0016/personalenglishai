package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.service.writing.impl.WritingScoreUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WritingScoreUtils — 评分工具类单元测试")
class WritingScoreUtilsTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 高考换算
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("computeGaokaoRaw — 百分制 → 高考实际分")
    class GaokaoRawTest {

        @Test
        @DisplayName("优秀作文 free 模式：90分 → 14/15")
        void excellent_free() {
            assertThat(WritingScoreUtils.computeGaokaoRaw(90, "free")).isEqualTo(14);
        }

        @Test
        @DisplayName("中等作文 free 模式：60分 → 9/15")
        void average_free() {
            assertThat(WritingScoreUtils.computeGaokaoRaw(60, "free")).isEqualTo(9);
        }

        @Test
        @DisplayName("满分 exam 模式：100分 → 25/25")
        void perfect_exam() {
            assertThat(WritingScoreUtils.computeGaokaoRaw(100, "exam")).isEqualTo(25);
        }

        @Test
        @DisplayName("极差 exam 模式：20分 → 5/25")
        void poor_exam() {
            assertThat(WritingScoreUtils.computeGaokaoRaw(20, "exam")).isEqualTo(5);
        }

        @Test
        @DisplayName("0分应返回0")
        void zero() {
            assertThat(WritingScoreUtils.computeGaokaoRaw(0, "free")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("computeGaokaoband — 档次标签")
    class Gaokaoband {

        @Test
        @DisplayName("14/15 → 优秀（≥87%）")
        void excellent() {
            assertThat(WritingScoreUtils.computeGaokaoband(14, "free")).isEqualTo("优秀");
        }

        @Test
        @DisplayName("11/15 → 良好（≥67%）")
        void good() {
            assertThat(WritingScoreUtils.computeGaokaoband(11, "free")).isEqualTo("良好");
        }

        @Test
        @DisplayName("9/15 → 中等（≥47%）")
        void average() {
            assertThat(WritingScoreUtils.computeGaokaoband(9, "free")).isEqualTo("中等");
        }

        @Test
        @DisplayName("5/15 → 偏低（≥27%）")
        void low() {
            // 4/15 = 26.7% < 27% → "需要提高"; 5/15 = 33.3% ≥ 27% → "偏低"
            assertThat(WritingScoreUtils.computeGaokaoband(5, "free")).isEqualTo("偏低");
        }

        @Test
        @DisplayName("2/15 → 需要提高（<27%）")
        void veryLow() {
            assertThat(WritingScoreUtils.computeGaokaoband(2, "free")).isEqualTo("需要提高");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 等级标准化
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("normalizeLevel — 等级字符串标准化")
    class NormalizeLevelTest {

        @Test @DisplayName("合法大写等级直接返回") void validUpper()  { assertThat(WritingScoreUtils.normalizeLevel("A")).isEqualTo("A"); }
        @Test @DisplayName("小写自动转大写")        void lowercase()  { assertThat(WritingScoreUtils.normalizeLevel("b")).isEqualTo("B"); }
        @Test @DisplayName("null → C")             void nullInput()  { assertThat(WritingScoreUtils.normalizeLevel(null)).isEqualTo("C"); }
        @Test @DisplayName("空字符串 → C")          void empty()      { assertThat(WritingScoreUtils.normalizeLevel("")).isEqualTo("C"); }
        @Test @DisplayName("乱码 → C")              void garbage()    { assertThat(WritingScoreUtils.normalizeLevel("X")).isEqualTo("C"); }
        @Test @DisplayName("带空格 → 正确解析")      void whitespace() { assertThat(WritingScoreUtils.normalizeLevel(" E ")).isEqualTo("E"); }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 词数统计
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("countWords — 英文词数统计")
    class CountWordsTest {

        @Test
        @DisplayName("普通句子")
        void normalSentence() {
            assertThat(WritingScoreUtils.countWords("Nature is a essential part of our lives")).isEqualTo(8);
        }

        @Test
        @DisplayName("多余空白字符不影响计数")
        void extraWhitespace() {
            assertThat(WritingScoreUtils.countWords("  hello   world  ")).isEqualTo(2);
        }

        @Test
        @DisplayName("null → 0")
        void nullInput() {
            assertThat(WritingScoreUtils.countWords(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("空字符串 → 0")
        void empty() {
            assertThat(WritingScoreUtils.countWords("   ")).isEqualTo(0);
        }

        @Test
        @DisplayName("单词 → 1")
        void singleWord() {
            assertThat(WritingScoreUtils.countWords("Hello")).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EWA 指数加权平均
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ewa — 指数加权平均（旧70% + 新30%）")
    class EwaTest {

        @Test
        @DisplayName("首次评分（old=null）→ 直接返回新值")
        void firstEvaluation() {
            BigDecimal result = WritingScoreUtils.ewa(null, 80);
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(80));
        }

        @Test
        @DisplayName("新值为null → 返回旧值不变")
        void nullNewScore() {
            BigDecimal old = BigDecimal.valueOf(70);
            assertThat(WritingScoreUtils.ewa(old, null)).isEqualByComparingTo(old);
        }

        @Test
        @DisplayName("both null → 返回默认60")
        void bothNull() {
            assertThat(WritingScoreUtils.ewa(null, null)).isEqualByComparingTo(BigDecimal.valueOf(60));
        }

        @Test
        @DisplayName("连续两次评分：70→90 = 70×0.7 + 90×0.3 = 76")
        void secondEvaluation() {
            BigDecimal after = WritingScoreUtils.ewa(BigDecimal.valueOf(70), 90);
            assertThat(after).isEqualByComparingTo(new BigDecimal("76.00"));
        }

        @Test
        @DisplayName("持平：80→80 = 80（稳定）")
        void stable() {
            BigDecimal after = WritingScoreUtils.ewa(BigDecimal.valueOf(80), 80);
            assertThat(after).isEqualByComparingTo(new BigDecimal("80.00"));
        }

        @Test
        @DisplayName("下降：90→60 = 90×0.7 + 60×0.3 = 81（EWA 减缓下降）")
        void declining() {
            BigDecimal after = WritingScoreUtils.ewa(BigDecimal.valueOf(90), 60);
            assertThat(after).isEqualByComparingTo(new BigDecimal("81.00"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 进步消息
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildImprovementMessage — 进步对比文案")
    class ImprovementMessageTest {

        @Test @DisplayName("进步≥2分 → 正向鼓励")  void bigImprove()  { assertThat(WritingScoreUtils.buildImprovementMessage(3)).contains("提高了"); }
        @Test @DisplayName("进步1分 → 轻度鼓励")    void smallImprove(){ assertThat(WritingScoreUtils.buildImprovementMessage(1)).contains("稍有进步"); }
        @Test @DisplayName("持平0分 → 中性提示")    void same()        { assertThat(WritingScoreUtils.buildImprovementMessage(0)).contains("持平"); }
        @Test @DisplayName("下降1分 → 轻度提醒")    void smallDrop()   { assertThat(WritingScoreUtils.buildImprovementMessage(-1)).contains("波动"); }
        @Test @DisplayName("下降≥2分 → 提示加强练习") void bigDrop()    { assertThat(WritingScoreUtils.buildImprovementMessage(-3)).contains("需加强"); }
    }
}
