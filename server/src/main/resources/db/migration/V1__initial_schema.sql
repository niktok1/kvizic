-- The first schema: the statements SchemaUtils.createStatements(*appTables) generates for the
-- definitions in Tables.kt, with only whitespace added and keywords in upper case.
--
-- One script serves both engines. The identifiers are unquoted and none is an SQL keyword, so
-- PostgreSQL folds them to lower case and H2 to upper case, which is what Exposed's own statements for
-- each engine produce. SchemaDriftTest holds this to the table definitions, names included.
--
-- Never edit this file once it has shipped: Flyway refuses to boot on a changed checksum. A change to
-- the schema is a new migration.

CREATE TABLE IF NOT EXISTS players (
    id VARCHAR(36) PRIMARY KEY,
    created_at BIGINT NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    name_source VARCHAR(16) NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id VARCHAR(36) PRIMARY KEY,
    player_id VARCHAR(36) NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    refresh_token_expires_at BIGINT NOT NULL,
    previous_refresh_token_hash VARCHAR(64) NULL,
    previous_refresh_token_expires_at BIGINT NULL,
    previous_refresh_token_rotated_at BIGINT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT fk_sessions_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
ALTER TABLE sessions ADD CONSTRAINT sessions_refresh_token_hash_unique UNIQUE (refresh_token_hash);
ALTER TABLE sessions ADD CONSTRAINT sessions_previous_refresh_token_hash_unique UNIQUE (previous_refresh_token_hash);
CREATE INDEX sessions_player_id_id ON sessions (player_id, id);

CREATE TABLE IF NOT EXISTS identities (
    provider VARCHAR(16),
    subject VARCHAR(255),
    player_id VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT pk_identities PRIMARY KEY (provider, subject),
    CONSTRAINT fk_identities_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
ALTER TABLE identities ADD CONSTRAINT identities_player_id_provider_unique UNIQUE (player_id, provider);

CREATE TABLE IF NOT EXISTS profiles (
    player_id VARCHAR(36) PRIMARY KEY,
    avatar_id VARCHAR(32) NOT NULL,
    games_played INT DEFAULT 0 NOT NULL,
    games_won INT DEFAULT 0 NOT NULL,
    answers_given INT DEFAULT 0 NOT NULL,
    answers_correct INT DEFAULT 0 NOT NULL,
    solo_runs INT DEFAULT 0 NOT NULL,
    solo_best_score INT NULL,
    solo_best_at BIGINT NULL,
    CONSTRAINT fk_profiles_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS topics (
    id VARCHAR(32) PRIMARY KEY,
    name_sr VARCHAR(40) NOT NULL,
    name_en VARCHAR(40) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS questions (
    id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    text VARCHAR(200) NOT NULL,
    correct_slot INT NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    explanation VARCHAR(300) NULL,
    source_url VARCHAR(500) NULL,
    lang VARCHAR(16) NOT NULL,
    author VARCHAR(64) NOT NULL,
    import_batch VARCHAR(64) NULL,
    import_key VARCHAR(64) NULL,
    revision INT DEFAULT 1 NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    reviewed_at BIGINT NULL,
    rejection_reason VARCHAR(200) NULL
);
ALTER TABLE questions ADD CONSTRAINT questions_import_key_unique UNIQUE (import_key);
CREATE INDEX questions_status_id ON questions (status, id);

CREATE TABLE IF NOT EXISTS question_options (
    question_id VARCHAR(36),
    slot INT,
    text VARCHAR(80) NOT NULL,
    CONSTRAINT pk_question_options PRIMARY KEY (question_id, slot),
    CONSTRAINT fk_question_options_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS question_topics (
    question_id VARCHAR(36),
    topic_id VARCHAR(32),
    CONSTRAINT pk_question_topics PRIMARY KEY (question_id, topic_id),
    CONSTRAINT fk_question_topics_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_question_topics_topic_id__id FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX question_topics_topic_id_question_id ON question_topics (topic_id, question_id);

CREATE TABLE IF NOT EXISTS question_stats (
    question_id VARCHAR(36) PRIMARY KEY,
    times_shown INT DEFAULT 0 NOT NULL,
    times_answered INT DEFAULT 0 NOT NULL,
    times_correct INT DEFAULT 0 NOT NULL,
    total_correct_ms BIGINT DEFAULT 0 NOT NULL,
    last_shown_at BIGINT NULL,
    CONSTRAINT fk_question_stats_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS seen_questions (
    player_id VARCHAR(36),
    question_id VARCHAR(36),
    last_seen_at BIGINT NOT NULL,
    times_seen INT DEFAULT 1 NOT NULL,
    CONSTRAINT pk_seen_questions PRIMARY KEY (player_id, question_id),
    CONSTRAINT fk_seen_questions_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_seen_questions_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX seen_questions_question_id_player_id ON seen_questions (question_id, player_id);
CREATE INDEX seen_questions_last_seen_at ON seen_questions (last_seen_at);

CREATE TABLE IF NOT EXISTS matches (
    id VARCHAR(36) PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    started_at BIGINT NOT NULL,
    ended_at BIGINT NOT NULL,
    question_count INT NOT NULL,
    seconds_per_question INT NOT NULL,
    topics VARCHAR(400) NOT NULL,
    participants INT NOT NULL,
    ended_early BOOLEAN NOT NULL
);
CREATE INDEX matches_ended_at ON matches (ended_at);

CREATE TABLE IF NOT EXISTS match_players (
    match_id VARCHAR(36),
    player_id VARCHAR(36),
    score INT NOT NULL,
    correct INT NOT NULL,
    answered INT NOT NULL,
    standing INT NOT NULL,
    finished BOOLEAN NOT NULL,
    CONSTRAINT pk_match_players PRIMARY KEY (match_id, player_id),
    CONSTRAINT fk_match_players_match_id__id FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_match_players_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX match_players_player_id_match_id ON match_players (player_id, match_id);

CREATE TABLE IF NOT EXISTS match_questions (
    match_id VARCHAR(36),
    slot INT,
    question_id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_match_questions PRIMARY KEY (match_id, slot),
    CONSTRAINT fk_match_questions_match_id__id FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_match_questions_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX match_questions_question_id_match_id ON match_questions (question_id, match_id);

CREATE TABLE IF NOT EXISTS player_topic_stats (
    player_id VARCHAR(36),
    topic_id VARCHAR(32),
    answered INT DEFAULT 0 NOT NULL,
    correct INT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_player_topic_stats PRIMARY KEY (player_id, topic_id),
    CONSTRAINT fk_player_topic_stats_player_id__id FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_player_topic_stats_topic_id__id FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE INDEX player_topic_stats_topic_id_player_id ON player_topic_stats (topic_id, player_id);

CREATE TABLE IF NOT EXISTS reports (
    id VARCHAR(36) PRIMARY KEY,
    question_id VARCHAR(36) NOT NULL,
    reporter_id VARCHAR(36) NULL,
    reason VARCHAR(16) NOT NULL,
    question_revision INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    resolution VARCHAR(16) NULL,
    created_at BIGINT NOT NULL,
    resolved_at BIGINT NULL,
    CONSTRAINT fk_reports_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_reports_reporter_id__id FOREIGN KEY (reporter_id) REFERENCES players(id) ON DELETE SET NULL ON UPDATE RESTRICT
);
ALTER TABLE reports ADD CONSTRAINT reports_question_id_reporter_id_question_revision_unique UNIQUE (question_id, reporter_id, question_revision);
CREATE INDEX reports_status_created_at ON reports (status, created_at);
CREATE INDEX reports_question_id_status ON reports (question_id, status);
CREATE INDEX reports_reporter_id ON reports (reporter_id);
