-- When a player last did anything with the server (signed in, refreshed, took a seat), so the moderator can
-- tell the players who play from accounts nobody has opened in months. Kept to the minute at most
-- (`PlayerStore.touch`). Null for a player who has not been seen since this script; the moderation app
-- shows such an account as seen at its creation.

ALTER TABLE players ADD COLUMN last_seen_at BIGINT NULL;
