package com.personalenglishai.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DictionaryContentMapper {

    List<Map<String, Object>> selectActiveEntriesByNormalizedHeadword(@Param("normalizedHeadword") String normalizedHeadword);

    List<Map<String, Object>> selectPronunciationsByEntryUids(@Param("entryUids") List<String> entryUids);

    List<Map<String, Object>> selectSensesByEntryUids(@Param("entryUids") List<String> entryUids);

    List<Map<String, Object>> selectExamplesByEntryUids(@Param("entryUids") List<String> entryUids);

    List<Map<String, Object>> selectPhrasesByEntryUids(@Param("entryUids") List<String> entryUids);
}
