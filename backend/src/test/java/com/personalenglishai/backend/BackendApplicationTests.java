package com.personalenglishai.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "vocabulary.generation.scheduler.enabled=false",
        "jwt.secret=test-jwt-secret-for-context-loads-32-bytes"
})
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
