-- Наши простори goes (the owner, 2026-10-01): a region, not a subject, so each of its questions belongs to a
-- subject as well, and the topic sat oddly among them. Its food and drink get a subject of their own, Храна
-- и пиће, in Забава, listed after every topic there was.
--
-- A question filed under Наши простори beside another topic keeps that one; one filed under it alone moves to
-- Географија, the nearest subject, for the moderator to file anew. No database holds one today: production's
-- bank is empty, and dev's, in memory, is seeded only after the migrations. A player's answers in the topic go
-- with it, there being none for the same reason.

INSERT INTO topics (id, name_sr, name_en, created_at, group_id) VALUES ('FOOD', 'Храна и пиће', 'Food & drink', 1790812800008, 'ENTERTAINMENT');

DELETE FROM question_topics WHERE topic_id = 'LOCAL' AND question_id IN (SELECT question_id FROM question_topics WHERE topic_id <> 'LOCAL');
UPDATE question_topics SET topic_id = 'GEOGRAPHY' WHERE topic_id = 'LOCAL';
DELETE FROM player_topic_stats WHERE topic_id = 'LOCAL';
DELETE FROM topics WHERE id = 'LOCAL';
DELETE FROM topic_groups WHERE id = 'REGION';
