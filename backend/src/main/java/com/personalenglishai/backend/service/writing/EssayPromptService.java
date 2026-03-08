package com.personalenglishai.backend.service.writing;

import com.personalenglishai.backend.entity.EssayPrompt;
import com.personalenglishai.backend.mapper.EssayPromptMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EssayPromptService {

    private final EssayPromptMapper essayPromptMapper;

    public EssayPromptService(EssayPromptMapper essayPromptMapper) {
        this.essayPromptMapper = essayPromptMapper;
    }

    public List<EssayPrompt> listByStage(Integer stageId) {
        return essayPromptMapper.selectActiveByStageId(stageId);
    }

    public List<EssayPrompt> search(Integer stageId, String keyword, Integer examYear, int page, int size) {
        int offset = (page - 1) * size;
        return essayPromptMapper.searchActiveByStageId(stageId, keyword, examYear, offset, size);
    }

    public long countSearch(Integer stageId, String keyword, Integer examYear) {
        return essayPromptMapper.countSearchActiveByStageId(stageId, keyword, examYear);
    }

    public EssayPrompt getByPaper(Integer stageId, String paper) {
        return essayPromptMapper.selectByPaper(stageId, paper);
    }

    public List<Integer> getAvailableYears(Integer stageId) {
        return essayPromptMapper.selectDistinctYearsByStageId(stageId);
    }
}
