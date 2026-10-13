-- Countries and languages: the country decides which questions a player is asked, the language how they
-- read. A question with no countries is common, asked everywhere; one with some is asked only when every
-- player's country is one of them. A translation keeps the source's answer slots, so the right one is the
-- same in every language, and the question's revision it was made from, which an edit leaves behind.
-- Topics and their groups get a name in each language. A player keeps the country they last said, null
-- for never (Serbia), and a report the language its question was read in. Nothing reads them yet.
--
-- Drafted with ./gradlew :server:printSchema. `name` is the one identifier Exposed takes for a keyword: it
-- quotes it, in lower case on both engines, so the script does too, and both hold it as `name`.

CREATE TABLE IF NOT EXISTS question_countries (
    question_id VARCHAR(36),
    country VARCHAR(2),
    CONSTRAINT pk_question_countries PRIMARY KEY (question_id, country),
    CONSTRAINT fk_question_countries_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);
CREATE INDEX question_countries_country_question_id ON question_countries (country, question_id);

CREATE TABLE IF NOT EXISTS question_translations (
    question_id VARCHAR(36),
    lang VARCHAR(16),
    text VARCHAR(200) NOT NULL,
    explanation VARCHAR(300) NULL,
    source_revision INT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT pk_question_translations PRIMARY KEY (question_id, lang),
    CONSTRAINT fk_question_translations_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS question_option_translations (
    question_id VARCHAR(36),
    lang VARCHAR(16),
    slot INT,
    text VARCHAR(80) NOT NULL,
    CONSTRAINT pk_question_option_translations PRIMARY KEY (question_id, lang, slot),
    CONSTRAINT fk_question_option_translations_question_id__id FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS topic_names (
    topic_id VARCHAR(32),
    lang VARCHAR(16),
    "name" VARCHAR(40) NOT NULL,
    CONSTRAINT pk_topic_names PRIMARY KEY (topic_id, lang),
    CONSTRAINT fk_topic_names_topic_id__id FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS topic_group_names (
    group_id VARCHAR(32),
    lang VARCHAR(16),
    "name" VARCHAR(40) NOT NULL,
    CONSTRAINT pk_topic_group_names PRIMARY KEY (group_id, lang),
    CONSTRAINT fk_topic_group_names_group_id__id FOREIGN KEY (group_id) REFERENCES topic_groups(id) ON DELETE CASCADE ON UPDATE RESTRICT
);

ALTER TABLE players ADD COLUMN country VARCHAR(2) NULL;
ALTER TABLE reports ADD COLUMN lang VARCHAR(16) NULL;
