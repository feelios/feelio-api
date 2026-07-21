package com.korit.feelioapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CheckDbStateTest {
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void checkState() {
        try {
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class);
            System.out.println("Total transactions: " + total);
            
        } catch (Exception e) {
            System.out.println("Error querying transactions: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
