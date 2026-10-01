-- A solo run picks its level, and each level keeps its own best: an easy run never beats a hard one.
-- Every run was medium before this script, so the first columns stay the medium best, which builds
-- before it go on writing as they drain.

ALTER TABLE profiles ADD COLUMN solo_best_easy_score INT NULL;
ALTER TABLE profiles ADD COLUMN solo_best_easy_at BIGINT NULL;
ALTER TABLE profiles ADD COLUMN solo_best_hard_score INT NULL;
ALTER TABLE profiles ADD COLUMN solo_best_hard_at BIGINT NULL;
