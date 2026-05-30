package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.filter.JwtAuthenticationFilter;
import com.personalenglishai.backend.dto.admin.AdminPageResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryEntryDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryFavoriteItemResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryPhoneticDto;
import com.personalenglishai.backend.dto.dictionary.DictionaryWordStateResponse;
import com.personalenglishai.backend.interceptor.JwtInterceptor;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import com.personalenglishai.backend.service.dictionary.DictionaryWordStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DictionaryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DictionaryController")
class DictionaryControllerTest {

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockBean
    private DictionaryLookupService dictionaryLookupService;

    @MockBean
    private DictionaryWordStateService dictionaryWordStateService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @Test
    @DisplayName("returns lookup result in ApiResponse")
    void lookupSuccess() throws Exception {
        DictionaryLookupResponse response = new DictionaryLookupResponse();
        response.setWord("apple");
        response.setLanguage("en-gb");
        response.setPhonetics(List.of(new DictionaryPhoneticDto("apl", "https://audio.example/apple.mp3")));

        DictionaryEntryDto entry = new DictionaryEntryDto("Noun");
        entry.setDefinitions(List.of("the round fruit of a tree"));
        entry.setExamples(List.of("an apple tree"));
        response.setEntries(List.of(entry));

        when(dictionaryLookupService.lookup("apple", "en-gb")).thenReturn(response);

        mockMvc.perform(get("/api/dictionary/lookup")
                        .param("word", "apple")
                        .param("language", "en-gb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.word").value("apple"))
                .andExpect(jsonPath("$.data.language").value("en-gb"))
                .andExpect(jsonPath("$.data.source").value("oxford"))
                .andExpect(jsonPath("$.data.phonetics[0].text").value("apl"))
                .andExpect(jsonPath("$.data.entries[0].partOfSpeech").value("Noun"))
                .andExpect(jsonPath("$.data.entries[0].definitions[0]").value("the round fruit of a tree"));
    }

    @Test
    @DisplayName("rejects blank word")
    void rejectsBlankWord() throws Exception {
        mockMvc.perform(get("/api/dictionary/lookup")
                        .param("word", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400060"))
                .andExpect(jsonPath("$.message").value("请输入要查询的单词"));
    }

    @Test
    @DisplayName("maps not found errors")
    void mapsNotFound() throws Exception {
        when(dictionaryLookupService.lookup("missing", null))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.NOT_FOUND));

        mockMvc.perform(get("/api/dictionary/lookup")
                        .param("word", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404030"))
                .andExpect(jsonPath("$.message").value("未找到该单词"));
    }

    @Test
    @DisplayName("maps quota errors")
    void mapsQuotaExceeded() throws Exception {
        when(dictionaryLookupService.lookup("apple", null))
                .thenThrow(new DictionaryLookupException(DictionaryLookupException.Kind.QUOTA_EXCEEDED));

        mockMvc.perform(get("/api/dictionary/lookup")
                        .param("word", "apple"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("429020"))
                .andExpect(jsonPath("$.message").value("词典服务额度已用完，请稍后再试"));
    }

    @Test
    @DisplayName("updates favorite state")
    void updatesFavoriteState() throws Exception {
        DictionaryWordStateResponse response = new DictionaryWordStateResponse();
        response.setWord("apple");
        response.setLanguage("en-gb");
        response.setFavorite(true);
        response.setLookupCount(3);
        when(dictionaryWordStateService.setFavorite(7L, "apple", "en-gb", true)).thenReturn(response);

        mockMvc.perform(post("/api/dictionary/words/apple/favorite")
                        .requestAttr("userId", 7L)
                        .contentType("application/json")
                        .content("{\"favorite\":true,\"language\":\"en-gb\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.word").value("apple"))
                .andExpect(jsonPath("$.data.favorite").value(true))
                .andExpect(jsonPath("$.data.lookupCount").value(3));
    }

    @Test
    @DisplayName("lists favorite words")
    void listsFavoriteWords() throws Exception {
        DictionaryFavoriteItemResponse item = new DictionaryFavoriteItemResponse();
        item.setWord("spark");
        item.setLanguage("en-gb");
        item.setFavorite(true);
        item.setLookupCount(5);
        item.setPhonetic("spɑːk");
        item.setPartOfSpeech("noun");
        item.setMeaning("a very small burning piece of material；火花");
        when(dictionaryWordStateService.listFavorites(7L, "spa", 1, 10))
                .thenReturn(new AdminPageResponse<>(List.of(item), 1, 1, 10));

        mockMvc.perform(get("/api/dictionary/favorites")
                        .requestAttr("userId", 7L)
                        .param("keyword", "spa")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].word").value("spark"))
                .andExpect(jsonPath("$.data.items[0].lookupCount").value(5))
                .andExpect(jsonPath("$.data.items[0].meaning").value("a very small burning piece of material；火花"));
    }
}
