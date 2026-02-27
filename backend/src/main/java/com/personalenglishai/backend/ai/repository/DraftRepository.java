package com.personalenglishai.backend.ai.repository;

import com.personalenglishai.backend.ai.domain.Draft;

import java.util.Optional;

/**
 * 草稿仓储：按 draftId 查询，供 ContextBuilder 使用
 */
public interface DraftRepository {

    Optional<Draft> findById(String draftId);
}
