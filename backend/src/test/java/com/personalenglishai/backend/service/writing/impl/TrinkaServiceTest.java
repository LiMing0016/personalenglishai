package com.personalenglishai.backend.service.writing.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.writing.WritingEvaluateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrinkaService - top category parsing")
class TrinkaServiceTest {

    @Test
    @DisplayName("应解析 Trinka 顶层分类并写入 raw_engine_meta")
    void shouldParseTopCategoryIntoRawEngineMeta() {
        TrinkaService service = new TrinkaService(new ObjectMapper());
        String text = "The cartoon shows a social problem.";
        String body = """
                {
                  "status": true,
                  "response": [
                    {
                      "start_index": 0,
                      "sentence_result": [
                        {
                          "start_index": 12,
                          "end_index": 17,
                          "covered_text": "shows",
                          "output": [
                            {
                              "revised_text": "depicts",
                              "comment": "Change 'shows' to 'depicts'. ",
                              "type": 1,
                              "error_category": "Word Choice",
                              "cta_present": true
                            }
                          ],
                          "category_details_v2": {
                            "critical_error": false,
                            "lang_category": "Word Choice",
                            "category_id": 2,
                            "category_name": "Clarity"
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        Object parseResult = ReflectionTestUtils.invokeMethod(service, "parseResponse", body, text, "advanced");
        @SuppressWarnings("unchecked")
        List<WritingEvaluateResponse.ErrorDto> errors =
                (List<WritingEvaluateResponse.ErrorDto>) ReflectionTestUtils.invokeMethod(parseResult, "errors");

        assertThat(errors).hasSize(1);
        WritingEvaluateResponse.RawEngineMetaDto raw = errors.get(0).getRawEngineMeta();
        assertThat(raw).isNotNull();
        assertThat(raw.getTopCategoryId()).isEqualTo(2);
        assertThat(raw.getTopCategoryName()).isEqualTo("Clarity");
        assertThat(raw.getErrorCategory()).isEqualTo("Word Choice");
        assertThat(raw.getPipeline()).isEqualTo("advanced");
    }

    @Test
    @DisplayName("缺少顶层分类名称时应根据 ID 回填标准名称")
    void shouldBackfillTopCategoryNameFromId() {
        TrinkaService service = new TrinkaService(new ObjectMapper());
        String text = "This picture reflect a social problem.";
        String body = """
                {
                  "status": true,
                  "response": [
                    {
                      "start_index": 0,
                      "sentence_result": [
                        {
                          "start_index": 13,
                          "end_index": 20,
                          "covered_text": "reflect",
                          "output": [
                            {
                              "revised_text": "reflects",
                              "comment": "Replace the verb 'reflect' with 'reflects'.",
                              "type": 1,
                              "error_category": "Subject-verb agreement",
                              "cta_present": true
                            }
                          ],
                          "category_details_v2": {
                            "critical_error": false,
                            "lang_category": "Subject-Verb Agreement",
                            "category_id": 1
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        Object parseResult = ReflectionTestUtils.invokeMethod(service, "parseResponse", body, text, "basic");
        @SuppressWarnings("unchecked")
        List<WritingEvaluateResponse.ErrorDto> errors =
                (List<WritingEvaluateResponse.ErrorDto>) ReflectionTestUtils.invokeMethod(parseResult, "errors");

        assertThat(errors).hasSize(1);
        WritingEvaluateResponse.RawEngineMetaDto raw = errors.get(0).getRawEngineMeta();
        assertThat(raw).isNotNull();
        assertThat(raw.getTopCategoryId()).isEqualTo(1);
        assertThat(raw.getTopCategoryName()).isEqualTo("Correctness");
        assertThat(raw.getPipeline()).isEqualTo("basic");
    }
}
