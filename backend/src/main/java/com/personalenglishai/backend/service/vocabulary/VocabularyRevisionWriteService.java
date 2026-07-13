package com.personalenglishai.backend.service.vocabulary;

import com.personalenglishai.backend.entity.vocabulary.VocabularyCard;
import com.personalenglishai.backend.entity.vocabulary.VocabularyCardRevision;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyCardMapper;
import com.personalenglishai.backend.mapper.vocabulary.VocabularyRevisionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VocabularyRevisionWriteService {
    private final VocabularyCardMapper cards;
    private final VocabularyRevisionMapper revisions;

    public VocabularyRevisionWriteService(
            VocabularyCardMapper cards,
            VocabularyRevisionMapper revisions) {
        this.cards = cards;
        this.revisions = revisions;
    }

    @Transactional
    public WriteOutcome appendAndActivate(
            Long userId,
            VocabularyCard card,
            VocabularyCardRevision revision) {
        revisions.insertRevision(revision);
        int activated = cards.updateActiveRevision(
                userId,
                card.getCardUid(),
                revision.getBaseRevisionUid(),
                revision.getRevisionUid(),
                "ready",
                revision.getTemplateKey(),
                revision.getTemplateVersion(),
                revision.getThemeUid(),
                revision.getThemeVersion());
        if (activated == 1) {
            return WriteOutcome.ACTIVATED;
        }
        if (cards.markConflictCandidate(card.getCardUid(), revision.getRevisionUid()) != 1) {
            throw new IllegalStateException("stale vocabulary revision candidate could not be recorded");
        }
        return WriteOutcome.STALE;
    }

    public enum WriteOutcome {
        ACTIVATED,
        STALE
    }
}
