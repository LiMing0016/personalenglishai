package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.common.response.ApiResponse;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {

    private final DictionaryLookupService dictionaryLookupService;

    public DictionaryController(DictionaryLookupService dictionaryLookupService) {
        this.dictionaryLookupService = dictionaryLookupService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<DictionaryLookupResponse>> lookup(
            @RequestParam String word,
            @RequestParam(required = false) String language) {
        if (word == null || word.isBlank()) {
            return error("400060", "请输入要查询的单词", HttpStatus.BAD_REQUEST);
        }
        try {
            DictionaryLookupResponse response = dictionaryLookupService.lookup(word.trim(), normalizeLanguage(language));
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (DictionaryLookupException e) {
            return mapDictionaryError(e.getKind());
        }
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return language.trim();
    }

    private ResponseEntity<ApiResponse<DictionaryLookupResponse>> mapDictionaryError(DictionaryLookupException.Kind kind) {
        return switch (kind) {
            case NOT_FOUND -> error("404030", "未找到该单词", HttpStatus.NOT_FOUND);
            case QUOTA_EXCEEDED -> error("429020", "词典服务额度已用完，请稍后再试", HttpStatus.TOO_MANY_REQUESTS);
            case TIMEOUT -> error("504020", "词典服务响应超时", HttpStatus.GATEWAY_TIMEOUT);
            case RESPONSE_INVALID -> error("502022", "词典服务返回异常", HttpStatus.BAD_GATEWAY);
            case INVALID_CONFIG, FORBIDDEN -> error("502020", "词典服务配置不可用", HttpStatus.BAD_GATEWAY);
            case UPSTREAM_ERROR -> error("502021", "词典服务暂时不可用", HttpStatus.BAD_GATEWAY);
        };
    }

    private ResponseEntity<ApiResponse<DictionaryLookupResponse>> error(String code, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
