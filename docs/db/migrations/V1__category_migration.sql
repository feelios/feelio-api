-- 1. categories 테이블에 새 컬럼 추가 (is_active는 이미 있으므로 제외)
ALTER TABLE `categories` 
ADD COLUMN `user_id` BIGINT DEFAULT NULL AFTER `category_id`,
ADD COLUMN `temp_old_custom_id` BIGINT DEFAULT NULL;

-- 2. 기존 유니크 제약조건 변경 (user_id 포함)
ALTER TABLE `categories` DROP INDEX `uq_category_name_type`;
ALTER TABLE `categories` ADD UNIQUE KEY `uq_category_name_type_user` (`name`, `type`, `user_id`);

-- 3. 커스텀 카테고리 데이터 이사 (없는 컬럼은 기본값 0, 1로 직접 주입)
INSERT INTO `categories` (`user_id`, `name`, `type`, `is_fixed`, `is_budgetable`, `sort_order`, `is_active`, `temp_old_custom_id`, `created_at`)
SELECT `user_id`, `name`, `type`, 0, 1, 0, 1, `custom_category_id`, `created_at`
FROM `custom_categories`;

-- 4. category_orders 테이블 업데이트 (새로 발급된 ID로 교체)
UPDATE `category_orders` co
JOIN `categories` c ON co.category_id = c.temp_old_custom_id AND co.user_id = c.user_id
SET co.category_id = c.category_id
WHERE co.is_custom = 1;

-- 5. transactions 테이블 업데이트
UPDATE `transactions` t
JOIN `categories` c ON t.category_id = c.temp_old_custom_id AND t.user_id = c.user_id
SET t.category_id = c.category_id;

-- 6. 청소 (불필요한 테이블 및 임시 컬럼 삭제)
DROP TABLE `custom_categories`;
ALTER TABLE `category_orders` DROP COLUMN `is_custom`;
ALTER TABLE `category_orders` DROP INDEX `uq_cat_order_user`;
ALTER TABLE `category_orders` ADD UNIQUE KEY `uq_cat_order_user` (`user_id`, `type`, `category_id`);
ALTER TABLE `categories` DROP COLUMN `temp_old_custom_id`;

-- 7. 외래키(FK) 신규 적용 (통합된 카테고리 관련 제약만 추가, 기존 FK는 건드리지 않음)
ALTER TABLE `categories` ADD CONSTRAINT `fk_category_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`) ON DELETE CASCADE;
ALTER TABLE `category_orders` ADD CONSTRAINT `fk_cat_order_category` FOREIGN KEY (`category_id`) REFERENCES `categories`(`category_id`) ON DELETE CASCADE;
ALTER TABLE `transactions` ADD CONSTRAINT `fk_tx_category` FOREIGN KEY (`category_id`) REFERENCES `categories`(`category_id`);
