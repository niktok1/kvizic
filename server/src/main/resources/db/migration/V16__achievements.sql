ALTER TABLE profiles ADD COLUMN perfect_games INT NOT NULL DEFAULT 0;
ALTER TABLE profiles ADD COLUMN first_rights INT NOT NULL DEFAULT 0;

UPDATE profiles
SET perfect_games = (
    SELECT COUNT(*)
    FROM match_players mp
    JOIN matches m ON m.id = mp.match_id
    WHERE mp.player_id = profiles.player_id
      AND mp.finished
      AND NOT m.ended_early
      AND m.question_count >= 10
      AND mp.correct = m.question_count
);

CREATE TABLE IF NOT EXISTS player_titles (
    player_id VARCHAR(36),
    title_id VARCHAR(32),
    earned_at BIGINT NOT NULL,
    CONSTRAINT pk_player_titles PRIMARY KEY (player_id, title_id),
    CONSTRAINT fk_player_titles_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
