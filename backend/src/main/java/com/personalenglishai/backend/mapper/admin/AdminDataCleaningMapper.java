package com.personalenglishai.backend.mapper.admin;

import com.personalenglishai.backend.entity.admin.DataCleaningJob;
import com.personalenglishai.backend.entity.admin.DataCleaningSource;
import com.personalenglishai.backend.entity.admin.DictionaryImportJob;
import com.personalenglishai.backend.entity.admin.DictionaryLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDataCleaningMapper {
    int insertSource(DataCleaningSource source);

    int updateSourceMetadata(@Param("sourceUid") String sourceUid,
                             @Param("metadataJson") String metadataJson,
                             @Param("status") String status);

    DataCleaningSource selectSourceByUid(@Param("sourceUid") String sourceUid);

    DataCleaningSource selectSourceByCode(@Param("sourceCode") String sourceCode);

    List<DataCleaningSource> selectSources(@Param("sourceType") String sourceType);

    long countSources();

    int insertJob(DataCleaningJob job);

    int updateJob(DataCleaningJob job);

    DataCleaningJob selectJobByUid(@Param("jobUid") String jobUid);

    List<DataCleaningJob> selectJobs(@Param("sourceUid") String sourceUid,
                                     @Param("jobType") String jobType);

    List<Map<String, Object>> selectJobStatusCounts();

    int upsertDictionaryLibrary(DictionaryLibrary library);

    List<DictionaryLibrary> selectDictionaryLibraries();

    DictionaryLibrary selectDictionaryLibraryByUid(@Param("dictionaryUid") String dictionaryUid);

    int insertDictionaryImportJob(DictionaryImportJob job);

    int updateDictionaryImportJob(DictionaryImportJob job);

    DictionaryImportJob selectDictionaryImportJobByUid(@Param("importJobUid") String importJobUid);

    List<DictionaryImportJob> selectDictionaryImportJobs(@Param("dictionaryUid") String dictionaryUid);

    int updateDictionaryLibraryStatus(@Param("dictionaryUid") String dictionaryUid,
                                      @Param("status") String status);

    String selectDictionaryEntryUidBySource(@Param("dictionaryUid") String dictionaryUid,
                                            @Param("sourceEntryId") String sourceEntryId);

    int upsertDictionaryEntry(@Param("entry") Map<String, Object> entry);

    int deleteDictionaryEntryChildren(@Param("entryUid") String entryUid);

    int insertDictionaryPronunciation(@Param("row") Map<String, Object> row);

    int insertDictionarySense(@Param("row") Map<String, Object> row);

    int insertDictionaryExample(@Param("row") Map<String, Object> row);

    int insertDictionaryPhrase(@Param("row") Map<String, Object> row);

    int upsertDictionaryResource(@Param("row") Map<String, Object> row);

    List<Map<String, Object>> selectDictionaryEntrySamples(@Param("dictionaryUid") String dictionaryUid,
                                                           @Param("limit") int limit);
}
