-- Feelio DB Schema (st1_feelio_kmj)
-- 실제 운영 DB에서 추출한 스키마의 SSOT 문서. (DB가 이미 생성되어 있음 — 이 파일은 기록용)
-- 규칙: 이 스키마를 임의로 실행/변경하지 않는다. 변경은 팀 승인 후 별도 진행.
-- 엔진/charset: InnoDB / utf8mb4 / utf8mb4_unicode_ci
-- 변경: 2026-07-22 카테고리 외래키를 제외한 모든 테이블 참조 무결성(FK) 제약 추가.
-- 계약 §6 "상황(situations, N:M)"은 팀 결정으로 제거됨 → 관련 테이블 없음.

-- 1. users
CREATE TABLE `users` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_asset` bigint NOT NULL DEFAULT '0',
  `onboarding_done` tinyint(1) NOT NULL DEFAULT '0',
  `theme_mode` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LIGHT',
  `aurora_theme` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '블루',
  `status` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. social_accounts  (provider, provider_user_id 로 식별 — provider 토큰은 저장 안 함)
CREATE TABLE `social_accounts` (
  `social_account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_user_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connected_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`social_account_id`),
  UNIQUE KEY `uq_social_provider` (`provider`,`provider_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. refresh_tokens  (JWT refresh 저장 — 이슈 #3 연계)
CREATE TABLE `refresh_tokens` (
  `token_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uq_refresh_token` (`token_hash`),
  KEY `idx_refresh_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. notification_settings
CREATE TABLE `notification_settings` (
  `notification_setting_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `record_reminder` tinyint(1) NOT NULL DEFAULT '1',
  `weekly_report` tinyint(1) NOT NULL DEFAULT '1',
  `goal_nudge` tinyint(1) NOT NULL DEFAULT '0',
  `remind_time` varchar(5) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_setting_id`),
  UNIQUE KEY `uq_noti_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. terms_agreements  (약관별 1행)
CREATE TABLE `terms_agreements` (
  `agreement_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `terms_type` varchar(15) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agreed` tinyint(1) NOT NULL,
  `version` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `agreed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`agreement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. emotions  (고정 8종 · character_key = 말랑이 face)
CREATE TABLE `emotions` (
  `emotion_id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `color` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `character_key` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`emotion_id`),
  UNIQUE KEY `uq_emotion_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. categories  (EXPENSE / INCOME)
CREATE TABLE `categories` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_fixed` tinyint(1) NOT NULL DEFAULT '0',
  `is_budgetable` tinyint(1) NOT NULL DEFAULT '1',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uq_category_name_type` (`name`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. transactions
CREATE TABLE `transactions` (
  `transaction_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `emotion_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EXPENSE',
  `amount` int NOT NULL,
  `memo` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `occurred_at` datetime NOT NULL,
  `goal_id` bigint DEFAULT NULL,
  `is_settled` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`transaction_id`),
  KEY `idx_tx_user_occurred` (`user_id`,`occurred_at`),
  KEY `idx_tx_user_emotion` (`user_id`,`emotion_id`),
  KEY `idx_tx_user_category` (`user_id`,`category_id`),
  CONSTRAINT `chk_tx_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. goals
CREATE TABLE `goals` (
  `goal_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_amount` int NOT NULL,
  `current_amount` int NOT NULL DEFAULT '0',
  `initial_amount` bigint NOT NULL DEFAULT '0',
  `start_date` date DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `is_main` tinyint(1) NOT NULL DEFAULT '0',
  `status` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`goal_id`),
  KEY `idx_goals_user_main` (`user_id`,`is_main`),
  CONSTRAINT `chk_goal_amount` CHECK (((`target_amount` > 0) and (`current_amount` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. monthly_summaries  (홈 화면 월 합계 — 계약 §8)
CREATE TABLE `monthly_summaries` (
  `summary_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `year` int NOT NULL,
  `month` int NOT NULL,
  `total_income` int NOT NULL DEFAULT '0',
  `total_expense` int NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`summary_id`),
  UNIQUE KEY `uq_summary` (`user_id`,`year`,`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. ai_insights  (인사이트 문장 — 계약 §9)
CREATE TABLE `ai_insights` (
  `insight_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `year` int NOT NULL,
  `month` int NOT NULL,
  `insight_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`insight_id`),
  KEY `idx_ai_user_ym` (`user_id`,`year`,`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. custom_categories (사용자 생성 커스텀 카테고리)
CREATE TABLE `custom_categories` (
  `custom_category_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_fixed` tinyint(1) NOT NULL DEFAULT '0',
  `is_budgetable` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`custom_category_id`),
  KEY `idx_custom_cat_user` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. category_orders (공통 + 커스텀 통합 정렬 순서)
CREATE TABLE `category_orders` (
  `category_order_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  `is_custom` tinyint(1) NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`category_order_id`),
  UNIQUE KEY `uq_cat_order_user` (`user_id`, `type`, `category_id`, `is_custom`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ==========================================
-- 외래키(FOREIGN KEY) 제약 조건 추가
-- ==========================================
ALTER TABLE social_accounts ADD CONSTRAINT k_social_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE 
efresh_tokens ADD CONSTRAINT k_token_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE 
otification_settings ADD CONSTRAINT k_noti_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE 	erms_agreements ADD CONSTRAINT k_terms_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE goals ADD CONSTRAINT k_goals_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE monthly_summaries ADD CONSTRAINT k_summary_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE i_insights ADD CONSTRAINT k_insights_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE custom_categories ADD CONSTRAINT k_custom_cat_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE category_orders ADD CONSTRAINT k_cat_order_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

-- transactions 테이블 외래키 (category_id 제외)
ALTER TABLE 	ransactions ADD CONSTRAINT k_tx_users FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;
ALTER TABLE 	ransactions ADD CONSTRAINT k_tx_emotions FOREIGN KEY (emotion_id) REFERENCES emotions (emotion_id);
ALTER TABLE 	ransactions ADD CONSTRAINT k_tx_goals FOREIGN KEY (goal_id) REFERENCES goals (goal_id) ON DELETE SET NULL;
