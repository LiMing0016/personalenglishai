package com.personalenglishai.backend.ai.config;

import java.util.Locale;
import java.util.Map;

public final class AiProviderSelection {

    private final String activeProvider;
    private final Map<String, AiProviderProperties.Provider> providers;

    private AiProviderSelection(String activeProvider, Map<String, AiProviderProperties.Provider> providers) {
        this.activeProvider = normalize(activeProvider);
        this.providers = providers;
    }

    public static AiProviderSelection from(AiProviderProperties properties) {
        return new AiProviderSelection(
                properties == null ? null : properties.getActive(),
                properties == null ? Map.of() : properties.getProviders()
        );
    }

    public SelectedProvider resolve(String explicitProvider) {
        String requestedProvider = normalize(explicitProvider);
        String providerKey = requestedProvider == null ? activeProvider : requestedProvider;
        if (providerKey == null || providerKey.isBlank()) {
            throw new IllegalStateException("No active AI provider configured");
        }
        AiProviderProperties.Provider provider = providers.get(providerKey);
        if (provider == null) {
            throw new IllegalStateException("Unsupported AI provider: " + providerKey);
        }
        return new SelectedProvider(
                providerKey,
                provider.getApiKey(),
                provider.getBaseUrl(),
                provider.getModel(),
                provider.getImageModel()
        );
    }

    public SelectedProvider resolveOrNull(String explicitProvider) {
        String requestedProvider = normalize(explicitProvider);
        String providerKey = requestedProvider == null ? activeProvider : requestedProvider;
        if (providerKey == null || providerKey.isBlank()) {
            return null;
        }
        AiProviderProperties.Provider provider = providers.get(providerKey);
        if (provider == null) {
            return null;
        }
        return new SelectedProvider(
                providerKey,
                provider.getApiKey(),
                provider.getBaseUrl(),
                provider.getModel(),
                provider.getImageModel()
        );
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public record SelectedProvider(String provider, String apiKey, String baseUrl, String model, String imageModel) {
    }
}
