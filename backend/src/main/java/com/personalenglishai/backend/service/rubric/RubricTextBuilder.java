package com.personalenglishai.backend.service.rubric;

import com.personalenglishai.backend.dto.rubric.RubricRuleRow;
import com.personalenglishai.backend.entity.RubricVersion;
import com.personalenglishai.backend.mapper.RubricMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RubricTextBuilder {

    private static final String DEFAULT_RUBRIC_KEY = "highschool-v1";

    private final RubricMapper rubricMapper;
    private final RubricService rubricService;

    public RubricTextBuilder(RubricMapper rubricMapper, RubricService rubricService) {
        this.rubricMapper = rubricMapper;
        this.rubricService = rubricService;
    }

    public String buildRubricText(String stage, String mode) {
        String normalizedStage = rubricService.normalizeStage(stage);
        String normalizedMode = rubricService.normalizeMode(mode);

        RubricVersion activeVersion = rubricMapper.selectActiveVersionByStage(normalizedStage);
        if (activeVersion == null) {
            return "";
        }

        String rubricKey = activeVersion.getRubricKey() == null
                ? DEFAULT_RUBRIC_KEY
                : activeVersion.getRubricKey();

        List<RubricRuleRow> rows = rubricMapper.selectActiveRubricRows(
                normalizedStage,
                normalizedMode,
                rubricKey
        );
        if (rows == null || rows.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Rubric below is mandatory for grading:\n");
        sb.append("(stage=").append(normalizedStage)
                .append(", rubric=").append(rubricKey)
                .append(", mode=").append(normalizedMode)
                .append(")\n\n");

        String lastDimensionKey = null;
        for (RubricRuleRow row : rows) {
            if (!row.getDimensionKey().equals(lastDimensionKey)) {
                if (lastDimensionKey != null) {
                    sb.append('\n');
                }
                sb.append(row.getDimensionKey())
                        .append(" (")
                        .append(row.getDisplayName())
                        .append("):\n");
                lastDimensionKey = row.getDimensionKey();
            }
            sb.append(row.getLevel())
                    .append(": ")
                    .append(row.getCriteria())
                    .append('\n');
        }
        return sb.toString().trim();
    }
}
