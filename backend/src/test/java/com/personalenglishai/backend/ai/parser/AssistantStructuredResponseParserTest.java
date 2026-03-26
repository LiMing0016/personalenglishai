package com.personalenglishai.backend.ai.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.ai.assistant.AssistantAction;
import com.personalenglishai.backend.ai.assistant.AssistantStructuredResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantStructuredResponseParserTest {

    private final AssistantStructuredResponseParser parser =
            new AssistantStructuredResponseParser(new ObjectMapper());

    @Test
    void parseShouldExtractMessageActionsAndSummaryLines() {
        AssistantStructuredResponse response = parser.parse("""
                {
                  "message": "我已经帮你整理出下一步操作。",
                  "summary": ["先看分数概览", "再决定是否润色"],
                  "actions": [
                    {
                      "type": "replace_selection",
                      "label": "替换选中内容",
                      "text": "A more polished sentence."
                    },
                    {
                      "type": "open_panel",
                      "label": "打开范文",
                      "panel": "structure"
                    }
                  ]
                }
                """);

        assertThat(response.message()).isEqualTo("我已经帮你整理出下一步操作。");
        assertThat(response.summary()).containsExactly("先看分数概览", "再决定是否润色");
        assertThat(response.actions())
                .extracting(AssistantAction::type, AssistantAction::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("replace_selection", "替换选中内容"),
                        org.assertj.core.groups.Tuple.tuple("open_panel", "打开范文")
                );
        assertThat(response.actions().get(0).text()).isEqualTo("A more polished sentence.");
        assertThat(response.actions().get(1).panel()).isEqualTo("structure");
    }
}
