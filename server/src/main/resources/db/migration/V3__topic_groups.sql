-- Topic groups, which the picker lists topics under so hundreds stay easy to find: server data, in
-- list_order's order, with no build needed for a new one. A topic in no group is listed after every group.
-- The first four, and every topic there is filed under one.

CREATE TABLE IF NOT EXISTS topic_groups (
    id VARCHAR(32) PRIMARY KEY,
    name_sr VARCHAR(40) NOT NULL,
    name_en VARCHAR(40) NOT NULL,
    list_order INT NOT NULL
);

ALTER TABLE topics ADD group_id VARCHAR(32) NULL;
ALTER TABLE topics ADD CONSTRAINT fk_topics_group_id__id FOREIGN KEY (group_id) REFERENCES topic_groups(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE INDEX topics_group_id_id ON topics (group_id, id);

INSERT INTO topic_groups (id, name_sr, name_en, list_order) VALUES ('KNOWLEDGE', 'Знање', 'Knowledge', 0);
INSERT INTO topic_groups (id, name_sr, name_en, list_order) VALUES ('ENTERTAINMENT', 'Забава', 'Entertainment', 1);
INSERT INTO topic_groups (id, name_sr, name_en, list_order) VALUES ('SPORT', 'Спорт', 'Sport', 2);
INSERT INTO topic_groups (id, name_sr, name_en, list_order) VALUES ('REGION', 'Наши простори', 'Our region', 3);

UPDATE topics SET group_id = 'KNOWLEDGE' WHERE id IN ('GEOGRAPHY', 'HISTORY', 'SCIENCE', 'LANGUAGE');
UPDATE topics SET group_id = 'ENTERTAINMENT' WHERE id IN ('MUSIC', 'FILM_TV');
UPDATE topics SET group_id = 'SPORT' WHERE id = 'SPORT';
UPDATE topics SET group_id = 'REGION' WHERE id = 'LOCAL';
