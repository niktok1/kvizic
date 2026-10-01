-- The players a question waited for who gave no answer: a silence is not knowing, so it counts towards
-- how hard the question plays. Counted from this script on; the games before it are read as answered.

ALTER TABLE question_stats ADD COLUMN times_unanswered INT DEFAULT 0 NOT NULL;
