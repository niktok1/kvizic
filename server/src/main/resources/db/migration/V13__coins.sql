-- Coins: earned by the places a player takes in a room's finished game (`CoinRules`), spent in the shop. A
-- player's balance is a counter, every game's pay is kept beside its standing and every item bought with what
-- it cost, so the balance can be checked against both. Everyone starts with none.

ALTER TABLE profiles ADD COLUMN coins INT NOT NULL DEFAULT 0;
ALTER TABLE profiles ADD COLUMN frame_id VARCHAR(32) NULL;
ALTER TABLE profiles ADD COLUMN title_id VARCHAR(32) NULL;

ALTER TABLE match_players ADD COLUMN coins INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS player_items (
    player_id VARCHAR(36),
    item_id VARCHAR(32),
    price_paid INT NOT NULL,
    bought_at BIGINT NOT NULL,
    CONSTRAINT pk_player_items PRIMARY KEY (player_id, item_id),
    CONSTRAINT fk_player_items_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
