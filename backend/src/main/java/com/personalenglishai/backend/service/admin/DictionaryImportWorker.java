package com.personalenglishai.backend.service.admin;

import com.personalenglishai.backend.entity.admin.DataCleaningSource;
import com.personalenglishai.backend.entity.admin.DictionaryLibrary;

import java.util.Map;

@FunctionalInterface
public interface DictionaryImportWorker {
    Map<String, Object> importDictionary(DictionaryLibrary library, DataCleaningSource source, int importLimit);
}
