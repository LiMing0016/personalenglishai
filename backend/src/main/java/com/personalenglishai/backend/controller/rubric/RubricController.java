package com.personalenglishai.backend.controller.rubric;

import com.personalenglishai.backend.dto.rubric.RubricActiveResponse;
import com.personalenglishai.backend.service.rubric.RubricService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rubric")
public class RubricController {

    private final RubricService rubricService;

    public RubricController(RubricService rubricService) {
        this.rubricService = rubricService;
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveRubric(
            @RequestParam(defaultValue = "highschool") String stage,
            @RequestParam(defaultValue = "free") String mode
    ) {
        if (!rubricService.isSupportedMode(mode)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "mode must be free or exam"
            ));
        }
        String normalizedMode = rubricService.normalizeMode(mode);
        RubricActiveResponse response = rubricService.getActiveRubric(stage, normalizedMode);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}
