-- =========================================================================
-- V1__category_migration.sql
-- 카테고리 테이블 통합 및 외래키 전체 적용 마이그레이션 스크립트
-- 주의: 이 스크립트는 DB 데이터를 이동시키므로 반드시 백업 후 실행하세요.
-- =========================================================================

-- 1. categories 테이블에 새 컬럼 추가
ALTER TABLE `categories` 
ADD COLUMN `user_id` BIGINT DEFAULT NULL AFTER `category_id`,
ADD COLUMN `is_active` TINYINT(1) NOT NULL DEFAULT 1 AFTER `sort_order`,
ADD COLUMN `temp_old_custom_id` BIGINT DEFAULT NULL; -- 데이터 이사용 임시 컬럼

-- 2. 기존 유니크 제약조건 변경 (user_id 포함)
ALTER TABLE `categories` DROP INDEX `uq_category_name_type`;
ALTER TABLE `categories` ADD UNIQUE KEY `uq_category_name_type_user` (`name`, `type`, `user_id`);

-- 3. 커스텀 카테고리 데이터 이사 (Data Migration)
INSERT INTO `categories` (`user_id`, `name`, `type`, `is_fixed`, `is_budgetable`, `sort_order`, `is_active`, `temp_old_custom_id`, `created_at`)
SELECT `user_id`, `name`, `type`, `is_fixed`, `is_budgetable`, 0, 1, `custom_category_id`, `created_at`
FROM `custom_categories`;

-- 4. category_orders 테이블 업데이트 (새로 발급된 ID로 교체)
UPDATE `category_orders` co
JOIN `categories` c ON co.category_id = c.temp_old_custom_id AND co.user_id = c.user_id
SET co.category_id = c.category_id
WHERE co.is_custom = 1;

-- 5. transactions 테이블 업데이트
-- (기존 transactions 테이블에 is_custom 플래그가 없었기 때문에, 
--  이 쿼리는 '공통 카테고리 ID'와 '커스텀 카테고리 ID'가 우연히 겹친 경우 약간의 혼선이 있을 수 있으나
--  해당 유저가 만든 커스텀 카테고리 ID와 일치하는 경우 우선적으로 새 ID로 매핑합니다)
UPDATE `transactions` t
JOIN `categories` c ON t.category_id = c.temp_old_custom_id AND t.user_id = c.user_id
SET t.category_id = c.category_id;

-- 6. 청소 (불필요한 테이블 및 임시 컬럼 삭제)
DROP TABLE `custom_categories`;

ALTER TABLE `category_orders` DROP COLUMN `is_custom`;
ALTER TABLE `category_orders` DROP INDEX `uq_cat_order_user`;
ALTER TABLE `category_orders` ADD UNIQUE KEY `uq_cat_order_user` (`user_id`, `type`, `category_id`);

ALTER TABLE `categories` DROP COLUMN `temp_old_custom_id`;

-- 7. 외래키(FK) 전체 적용 (Integrity Lock)
ALTER TABLE `categories` ADD CONSTRAINT `fk_category_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `category_orders` ADD CONSTRAINT `fk_cat_order_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `category_orders` ADD CONSTRAINT `fk_cat_order_category` FOREIGN KEY (`category_id`) REFERENCES `categories`(`category_id`) ON DELETE CASCADE;
ALTER TABLE `social_accounts` ADD CONSTRAINT `fk_social_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `refresh_tokens` ADD CONSTRAINT `fk_refresh_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `notification_settings` ADD CONSTRAINT `fk_noti_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `terms_agreements` ADD CONSTRAINT `fk_terms_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `goals` ADD CONSTRAINT `fk_goals_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `monthly_summaries` ADD CONSTRAINT `fk_summary_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `ai_insights` ADD CONSTRAINT `fk_ai_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `transactions` ADD CONSTRAINT `fk_tx_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `transactions` ADD CONSTRAINT `fk_tx_emotion` FOREIGN KEY (`emotion_id`) REFERENCES `emotions`(`emotion_id`);
ALTER TABLE `transactions` ADD CONSTRAINT `fk_tx_category` FOREIGN KEY (`category_id`) REFERENCES `categories`(`category_id`);
ALTER TABLE `transactions` ADD CONSTRAINT `fk_tx_goal` FOREIGN KEY (`goal_id`) REFERENCES `goals`(`goal_id`) ON DELETE SET NULL;
