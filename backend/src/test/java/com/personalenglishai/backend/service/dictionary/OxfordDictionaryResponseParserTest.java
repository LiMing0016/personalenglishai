package com.personalenglishai.backend.service.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.service.dictionary.impl.OxfordDictionaryResponseParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OxfordDictionaryResponseParser")
class OxfordDictionaryResponseParserTest {

    private final OxfordDictionaryResponseParser parser = new OxfordDictionaryResponseParser(new ObjectMapper());

    @Test
    @DisplayName("parses phonetics, audio, parts of speech, definitions, and examples")
    void parsesOxfordWordsResponse() {
        String body = """
                {
                  "metadata": { "provider": "Oxford University Press" },
                  "results": [
                    {
                      "id": "apple",
                      "word": "apple",
                      "lexicalEntries": [
                        {
                          "language": "en-gb",
                          "lexicalCategory": { "id": "noun", "text": "Noun" },
                          "pronunciations": [
                            {
                              "phoneticSpelling": "apl",
                              "phoneticNotation": "IPA",
                              "audioFile": "https://audio.oxforddictionaries.com/apple.mp3"
                            }
                          ],
                          "entries": [
                            {
                              "pronunciations": [
                                { "phoneticSpelling": "apl-entry", "audioFile": "https://audio.oxforddictionaries.com/apple-entry.mp3" }
                              ],
                              "senses": [
                                {
                                  "definitions": ["the round fruit of a tree of the rose family"],
                                  "examples": [{ "text": "an apple tree" }]
                                },
                                {
                                  "definitions": ["the tree bearing apples"],
                                  "examples": [{ "text": "apple growers" }]
                                }
                              ]
                            }
                          ]
                        },
                        {
                          "language": "en-gb",
                          "lexicalCategory": { "id": "verb", "text": "Verb" },
                          "entries": [
                            {
                              "senses": [
                                {
                                  "definitions": ["to give the shape of an apple"],
                                  "examples": [{ "text": "the fruit apples as it ripens" }]
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        DictionaryLookupResponse response = parser.parse("apple", "en-gb", body);

        assertThat(response.getWord()).isEqualTo("apple");
        assertThat(response.getLanguage()).isEqualTo("en-gb");
        assertThat(response.getSource()).isEqualTo("oxford");
        assertThat(response.getPhonetics())
                .extracting("text")
                .contains("apl", "apl-entry");
        assertThat(response.getPhonetics())
                .extracting("audioUrl")
                .contains("https://audio.oxforddictionaries.com/apple.mp3");
        assertThat(response.getEntries()).hasSize(2);
        assertThat(response.getEntries().get(0).getPartOfSpeech()).isEqualTo("Noun");
        assertThat(response.getEntries().get(0).getDefinitions())
                .containsExactly("the round fruit of a tree of the rose family", "the tree bearing apples");
        assertThat(response.getEntries().get(0).getExamples())
                .containsExactly("an apple tree", "apple growers");
        assertThat(response.getEntries().get(1).getPartOfSpeech()).isEqualTo("Verb");
        assertThat(response.getEntries().get(1).getDefinitions())
                .containsExactly("to give the shape of an apple");
    }

    @Test
    @DisplayName("handles missing optional Oxford fields without failing")
    void handlesMissingOptionalFields() {
        String body = """
                {
                  "results": [
                    {
                      "word": "ability",
                      "lexicalEntries": [
                        {
                          "lexicalCategory": { "id": "noun" },
                          "entries": [
                            {
                              "senses": [
                                { "definitions": ["possession of the means or skill to do something"] }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        DictionaryLookupResponse response = parser.parse("ability", "en-gb", body);

        assertThat(response.getWord()).isEqualTo("ability");
        assertThat(response.getPhonetics()).isEmpty();
        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getEntries().get(0).getPartOfSpeech()).isEqualTo("noun");
        assertThat(response.getEntries().get(0).getDefinitions())
                .containsExactly("possession of the means or skill to do something");
        assertThat(response.getEntries().get(0).getExamples()).isEmpty();
    }
}
