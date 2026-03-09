package com.personalenglishai.backend.controller;

import com.personalenglishai.backend.entity.WritingStage;
import com.personalenglishai.backend.mapper.WritingStageMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/writing/stage-config")
public class WritingStageController {

    private final WritingStageMapper writingStageMapper;

    public WritingStageController(WritingStageMapper writingStageMapper) {
        this.writingStageMapper = writingStageMapper;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getByCode(@PathVariable String code) {
        WritingStage stage = writingStageMapper.selectByCode(code);
        if (stage == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "code", stage.getCode(),
                "name", stage.getName(),
                "minWordCount", stage.getMinWordCount() != null ? stage.getMinWordCount() : 60
        ));
    }
}
