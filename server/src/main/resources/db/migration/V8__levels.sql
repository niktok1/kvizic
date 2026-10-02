-- A player's experience, earned in finished multiplayer games and never in a solo run. A level is worked out
-- from it (`Levels`), so the curve can change without a migration. Players from before this script start
-- with what their multiplayer games and wins would have earned; their correct answers cannot be told from
-- the ones in solo runs, and are left out.

ALTER TABLE profiles ADD COLUMN xp INT NOT NULL DEFAULT 0;

UPDATE profiles
SET xp = 20 * (CASE WHEN games_played > solo_runs THEN games_played - solo_runs ELSE 0 END) + 20 * games_won;
