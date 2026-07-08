-- Feelio 마스터 시드 (감정 8종 / 카테고리 EXPENSE·INCOME)
-- 근거:
--   emotions.color  = feelio-web/src/styles/theme.js  (emotionPalette[*].color)
--   emotions.character_key = feelio-web/src/components/common/EmotionBlob.jsx (말랑이 face)
--   categories = docs/API-CONTRACT.md §2 (SSOT)
-- 정렬: emotions/categories 모두 계약 §2 나열 순 = sort_order. AUTO_INCREMENT 순서 유지 위해 순서대로 INSERT.
-- 재실행 대비: name/(name,type) UNIQUE 로 중복 방지. 필요 시 아래 INSERT ... 를 재실행해도 UNIQUE 로 막힘.

-- 감정 8종 (고정, 커스텀 불가) — emotion_id 1~8 = 계약 §2 순서
INSERT INTO emotions (name, color, character_key, sort_order) VALUES
 ('신남',    '#FF8A62', 'excited', 1),
 ('설렘',    '#F28AB7', 'flutter', 2),
 ('뿌듯함',  '#F2C766', 'proud',   3),
 ('스트레스','#A68BEA', 'stress',  4),
 ('외로움',  '#76A7E8', 'lonely',  5),
 ('화남',    '#E87573', 'angry',   6),
 ('평온',    '#83C9B0', 'calm',    7),
 ('무덤덤',  '#AEB4C1', 'numb',    8);

-- 카테고리 (EXPENSE 8 + INCOME 3) — sort_order 는 타입별 순번
INSERT INTO categories (name, type, sort_order) VALUES
 ('식비', 'EXPENSE', 1),
 ('배달', 'EXPENSE', 2),
 ('카페', 'EXPENSE', 3),
 ('교통', 'EXPENSE', 4),
 ('쇼핑', 'EXPENSE', 5),
 ('문화', 'EXPENSE', 6),
 ('건강', 'EXPENSE', 7),
 ('기타', 'EXPENSE', 8),
 ('급여', 'INCOME',  1),
 ('용돈', 'INCOME',  2),
 ('기타', 'INCOME',  3);
