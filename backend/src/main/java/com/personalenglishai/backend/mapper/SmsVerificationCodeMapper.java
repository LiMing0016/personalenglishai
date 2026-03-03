package com.personalenglishai.backend.mapper;

import com.personalenglishai.backend.entity.SmsVerificationCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SmsVerificationCodeMapper {

    int insert(SmsVerificationCode record);

    SmsVerificationCode findLatest(@Param("phone") String phone, @Param("purpose") String purpose);

    int markUsed(@Param("id") Long id);

    int invalidateByPhone(@Param("phone") String phone, @Param("purpose") String purpose);

    int countRecent(@Param("phone") String phone, @Param("minutesAgo") int minutesAgo);
}
