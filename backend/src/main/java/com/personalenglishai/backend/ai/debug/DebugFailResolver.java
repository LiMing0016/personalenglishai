package com.personalenglishai.backend.ai.debug;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Optional;

@Component
public class DebugFailResolver {

    private static final Logger log = LoggerFactory.getLogger(DebugFailResolver.class);
    private static final String HEADER_NAME = "X-Debug-Fail";

    private final Environment environment;

    public DebugFailResolver(Environment environment) {
        this.environment = environment;
    }

    public Optional<Integer> resolveFailCode() {
        if (!isDevOrLocalProfile()) {
            return Optional.empty();
        }

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttrs)) {
            return Optional.empty();
        }

        HttpServletRequest request = servletAttrs.getRequest();
        String raw = request.getHeader(HEADER_NAME);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        try {
            int code = Integer.parseInt(raw.trim());
            if (code < 400 || code > 599) {
                log.debug("Ignore out-of-range {} header value: {}", HEADER_NAME, code);
                return Optional.empty();
            }
            return Optional.of(code);
        } catch (NumberFormatException ex) {
            log.debug("Ignore invalid {} header value: {}", HEADER_NAME, raw);
            return Optional.empty();
        }
    }

    private boolean isDevOrLocalProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(p -> p.equals("dev") || p.equals("local"));
    }
}
