package com.personalenglishai.backend.service.dictionary.impl;

import com.personalenglishai.backend.config.OxfordDictionaryProperties;
import com.personalenglishai.backend.dto.dictionary.DictionaryLookupResponse;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupException;
import com.personalenglishai.backend.service.dictionary.DictionaryLookupService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class OxfordDictionaryService implements DictionaryLookupService {
    private static final Logger log = LoggerFactory.getLogger(OxfordDictionaryService.class);

    private final OxfordDictionaryProperties properties;
    private final OxfordDictionaryResponseParser parser;
    private HttpClient httpClient;

    public OxfordDictionaryService(
            OxfordDictionaryProperties properties,
            OxfordDictionaryResponseParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    @PostConstruct
    void initHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())))
                .build();
    }

    @Override
    public DictionaryLookupResponse lookup(String word, String language) {
        if (isBlank(properties.getAppId()) || isBlank(properties.getAppKey())) {
            throw new DictionaryLookupException(DictionaryLookupException.Kind.INVALID_CONFIG);
        }
        String normalizedWord = word == null ? "" : word.trim();
        String resolvedLanguage = isBlank(language) ? properties.getLanguage() : language.trim();
        try {
            long start = System.currentTimeMillis();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(resolvedLanguage, normalizedWord))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("app_id", properties.getAppId())
                    .header("app_key", properties.getAppKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            log.info("Oxford lookup finished. word={} language={} status={} elapsed={}ms",
                    normalizedWord, resolvedLanguage, response.statusCode(), elapsed);
            return switch (response.statusCode()) {
                case 200 -> parse(normalizedWord, resolvedLanguage, response.body());
                case 404 -> throwException(DictionaryLookupException.Kind.NOT_FOUND);
                case 403 -> throwException(DictionaryLookupException.Kind.FORBIDDEN);
                case 429 -> throwException(DictionaryLookupException.Kind.QUOTA_EXCEEDED);
                default -> throwException(DictionaryLookupException.Kind.UPSTREAM_ERROR);
            };
        } catch (DictionaryLookupException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new DictionaryLookupException(DictionaryLookupException.Kind.TIMEOUT, e);
        } catch (IllegalArgumentException e) {
            throw new DictionaryLookupException(DictionaryLookupException.Kind.RESPONSE_INVALID, e);
        } catch (Exception e) {
            throw new DictionaryLookupException(DictionaryLookupException.Kind.UPSTREAM_ERROR, e);
        }
    }

    private DictionaryLookupResponse parse(String word, String language, String body) {
        try {
            return parser.parse(word, language, body);
        } catch (IllegalArgumentException e) {
            throw new DictionaryLookupException(DictionaryLookupException.Kind.RESPONSE_INVALID, e);
        }
    }

    private URI buildUri(String language, String word) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String encodedLanguage = UriUtils.encodePathSegment(language, StandardCharsets.UTF_8);
        String encodedWord = UriUtils.encodeQueryParam(word, StandardCharsets.UTF_8);
        return URI.create(normalizedBaseUrl + "/words/" + encodedLanguage + "?q=" + encodedWord);
    }

    private DictionaryLookupResponse throwException(DictionaryLookupException.Kind kind) {
        throw new DictionaryLookupException(kind);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
