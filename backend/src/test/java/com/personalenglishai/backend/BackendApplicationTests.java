package com.personalenglishai.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "vocabulary.generation.scheduler.enabled=false")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
