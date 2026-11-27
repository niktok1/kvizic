-- The host crown a player wears from the shop (`Shop`), null for the plain one. Bought crowns are `player_items`
-- rows like every other item.
--
-- The shop's first catalogue leaves it (the owner, 2026-11-27): titles are to be earned, not bought, and frames and
-- animals are a new set. What was bought of it, on dev alone, is paid back and taken off.

ALTER TABLE profiles ADD COLUMN crown_id VARCHAR(32) NULL;

UPDATE profiles
SET coins = coins + (
    SELECT COALESCE(SUM(player_items.price_paid), 0)
    FROM player_items
    WHERE player_items.player_id = profiles.player_id
      AND (player_items.item_id LIKE 'title.%'
        OR player_items.item_id IN ('frame.double', 'frame.dashes', 'frame.bulbs', 'frame.stars', 'frame.rays',
                                    'frame.laurel', 'boar', 'beaver', 'jackal'))
);

DELETE FROM player_items
WHERE item_id LIKE 'title.%'
   OR item_id IN ('frame.double', 'frame.dashes', 'frame.bulbs', 'frame.stars', 'frame.rays', 'frame.laurel', 'boar',
                  'beaver', 'jackal');

UPDATE profiles SET title_id = NULL;
UPDATE profiles SET frame_id = NULL
WHERE frame_id IN ('frame.double', 'frame.dashes', 'frame.bulbs', 'frame.stars', 'frame.rays', 'frame.laurel');
UPDATE profiles SET avatar_id = 'fox' WHERE avatar_id IN ('boar', 'beaver', 'jackal');
