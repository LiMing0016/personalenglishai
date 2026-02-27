package com.personalenglishai.backend.ai.prompt;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReferenceResolver {

    private static final Pattern CN_PARAGRAPH = Pattern.compile("第\\s*(\\d+)\\s*段");
    private static final Pattern CN_SENTENCE = Pattern.compile("第\\s*(\\d+)\\s*句");
    private static final Pattern EN_PARAGRAPH = Pattern.compile("(?:paragraph\\s*#?\\s*|para\\s*#?\\s*)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_SENTENCE = Pattern.compile("sentence\\s*#?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public ReferenceResolution resolve(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ReferenceResolution.none();
        }
        String raw = userMessage.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        Matcher m;
        m = CN_PARAGRAPH.matcher(raw);
        if (m.find()) {
            return new ReferenceResolution(ReferenceType.PARAGRAPH_N, parseInt(m.group(1)), 0.95d, "cn_paragraph");
        }
        m = EN_PARAGRAPH.matcher(lower);
        if (m.find()) {
            return new ReferenceResolution(ReferenceType.PARAGRAPH_N, parseInt(m.group(1)), 0.95d, "en_paragraph");
        }
        m = CN_SENTENCE.matcher(raw);
        if (m.find()) {
            return new ReferenceResolution(ReferenceType.SENTENCE_N, parseInt(m.group(1)), 0.95d, "cn_sentence");
        }
        m = EN_SENTENCE.matcher(lower);
        if (m.find()) {
            return new ReferenceResolution(ReferenceType.SENTENCE_N, parseInt(m.group(1)), 0.95d, "en_sentence");
        }

        if (containsAny(raw,
                "上一条回复", "上一条回答", "上一个回答", "刚才的回答",
                "上一句聊天内容", "上一句", "上一条聊天内容", "上一条聊天") ||
            containsAny(lower, "last reply", "last answer", "previous answer", "previous reply")) {
            return new ReferenceResolution(ReferenceType.LAST_ASSISTANT, null, 0.9d, "last_assistant");
        }

        if (containsAny(raw, "这个词", "这个表达", "这个短语", "该词", "这个单词") ||
            containsAny(lower, "this word", "this phrase", "this expression")) {
            return new ReferenceResolution(ReferenceType.THIS_WORD, null, 0.85d, "this_word");
        }

        if (containsAny(raw, "上面", "前面", "刚才", "这段", "这篇", "那句", "那段", "上文", "左边作文", "左侧作文") ||
            containsAny(lower, "above", "previous", "this paragraph", "that paragraph", "the above")) {
            return new ReferenceResolution(ReferenceType.ABOVE, null, 0.7d, "above");
        }

        return ReferenceResolution.none();
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    public enum ReferenceType {
        NONE,
        ABOVE,
        LAST_ASSISTANT,
        PARAGRAPH_N,
        SENTENCE_N,
        THIS_WORD
    }

    public record ReferenceResolution(ReferenceType type, Integer index, double confidence, String ruleTag) {
        public static ReferenceResolution none() {
            return new ReferenceResolution(ReferenceType.NONE, null, 0.0d, "none");
        }

        public boolean hasReference() {
            return type != null && type != ReferenceType.NONE;
        }
    }
}
