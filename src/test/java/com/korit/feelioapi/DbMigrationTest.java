package com.korit.feelioapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DbMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void executeMigration() {
        try {
            jdbcTemplate.execute("ALTER TABLE transactions ADD COLUMN is_settled TINYINT(1) NOT NULL DEFAULT 0");
            System.out.println("Added is_settled column successfully.");
        } catch (Exception e) {
            System.out.println("is_settled column might already exist: " + e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE categories ADD COLUMN is_fixed TINYINT(1) NOT NULL DEFAULT 0");
            System.out.println("Added is_fixed column successfully.");
        } catch (Exception e) {
            System.out.println("is_fixed column might already exist: " + e.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE categories ADD COLUMN is_budgetable TINYINT(1) NOT NULL DEFAULT 1");
            System.out.println("Added is_budgetable column successfully.");
        } catch (Exception e) {
            System.out.println("is_budgetable column might already exist: " + e.getMessage());
        }

        try {
            jdbcTemplate.execute("UPDATE categories SET is_budgetable = 0 WHERE name IN ('더치페이', '저축')");
            System.out.println("Updated budgetable status for specific categories.");
        } catch (Exception e) {
            System.out.println("Failed to update budgetable status: " + e.getMessage());
        }

        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories WHERE name = '정산금' AND type = 'INCOME'", Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("INSERT INTO categories (name, type, is_active, sort_order) VALUES ('정산금', 'INCOME', 1, 99)");
                System.out.println("Added 정산금 category successfully.");
            } else {
                System.out.println("정산금 category already exists.");
            }
        } catch (Exception e) {
            System.out.println("Failed to add 정산금 category: " + e.getMessage());
        }
    }
}
