package com.personalenglishai.backend.service.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalenglishai.backend.config.OxfordDictionaryProperties;
import com.personalenglishai.backend.service.dictionary.impl.OxfordDictionaryResponseParser;
import com.personalenglishai.backend.service.dictionary.impl.OxfordDictionaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OxfordDictionaryService")
class OxfordDictionaryServiceTest {

    @Test
    @DisplayName("builds Oxford Words lookup URI with q query parameter")
    void buildsWordsLookupUriWithQueryParameter() {
        OxfordDictionaryProperties properties = new OxfordDictionaryProperties();
        properties.setBaseUrl("https://od-api-sandbox.oxforddictionaries.com/api/v2/");
        OxfordDictionaryService service = new OxfordDictionaryService(
                properties,
                new OxfordDictionaryResponseParser(new ObjectMapper()));

        URI uri = ReflectionTestUtils.invokeMethod(service, "buildUri", "en-gb", "apple");

        assertThat(uri).hasToString("https://od-api-sandbox.oxforddictionaries.com/api/v2/words/en-gb?q=apple");
    }
}
