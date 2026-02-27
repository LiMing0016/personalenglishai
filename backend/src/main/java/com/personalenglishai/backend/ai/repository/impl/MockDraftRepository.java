package com.personalenglishai.backend.ai.repository.impl;

import com.personalenglishai.backend.ai.domain.Draft;
import com.personalenglishai.backend.ai.repository.DraftRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 mock：可按需放入 draft，未放入的 id 返回 empty
 */
@Repository
public class MockDraftRepository implements DraftRepository {

    private final ConcurrentHashMap<String, Draft> store = new ConcurrentHashMap<>();

    public MockDraftRepository() {
        store.put("draft-001", new Draft("draft-001", "Sample draft content for testing."));
    }

    @Override
    public Optional<Draft> findById(String draftId) {
        return Optional.ofNullable(store.get(draftId));
    }

    /** 测试/联调用：写入 draft */
    public void put(Draft draft) {
        if (draft != null && draft.getId() != null) {
            store.put(draft.getId(), draft);
        }
    }
}
