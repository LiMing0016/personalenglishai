package com.personalenglishai.backend.service.writing.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class PromptSheetChartImageServiceSpringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.upload-dir=target/test-uploads",
                    "app.upload-public-path=/uploads"
            );

    @Test
    void springShouldCreateChartImageServiceWithConfiguredPaths() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(PromptSheetChartImageService.class));
    }

    @Configuration
    @Import(PromptSheetChartImageService.class)
    static class TestConfig {
    }
}
