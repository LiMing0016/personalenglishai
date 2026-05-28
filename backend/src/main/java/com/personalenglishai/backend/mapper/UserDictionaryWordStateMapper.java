package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.UserDictionaryWordState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserDictionaryWordStateMapper {

    int incrementLookup(@Param("userId") Long userId,
                        @Param("word") String word,
                        @Param("normalizedWord") String normalizedWord,
                        @Param("language") String language,
                        @Param("source") String source);

    int setFavorite(@Param("userId") Long userId,
                    @Param("word") String word,
                    @Param("normalizedWord") String normalizedWord,
                    @Param("language") String language,
                    @Param("favorite") boolean favorite);

    UserDictionaryWordState selectByUserAndWord(@Param("userId") Long userId,
                                                @Param("normalizedWord") String normalizedWord);

    List<UserDictionaryWordState> selectFavorites(@Param("userId") Long userId,
                                                  @Param("keyword") String keyword,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    long countFavorites(@Param("userId") Long userId,
                        @Param("keyword") String keyword);
}
