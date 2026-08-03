package com.korit.feelioapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("secret")
public class AlterTableTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void addFcmTokenColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN fcm_token VARCHAR(255) NULL");
            System.out.println("SUCCESS: fcm_token column added.");
        } catch (Exception e) {
            System.out.println("SKIPPED: " + e.getMessage());
        }
    }
}
