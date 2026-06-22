package com.example.foodrecommend.mapper;

import com.example.foodrecommend.entity.FeedbackIndexDlq;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Sql("classpath:db/schema.sql")
class FeedbackIndexDlqMapperIT {

    @Autowired
    private FeedbackIndexDlqMapper dlqMapper;

    @Test
    void dlq_mapper_insert_and_select() {
        FeedbackIndexDlq row = new FeedbackIndexDlq();
        row.setRecordId(999L);
        row.setError("test");
        row.setRetryCount(1);
        dlqMapper.insert(row);
        assertThat(dlqMapper.selectById(row.getId())).isNotNull();
    }
}
