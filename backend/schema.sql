CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DROP TABLE IF EXISTS hiragana_questions;
CREATE TABLE if not exists hiragana_questions
(
    id           UUID PRIMARY KEY,
    hiragana     VARCHAR(255) NOT NULL UNIQUE,
    romanization VARCHAR(255) NOT NULL,
    translation  VARCHAR(255),
    topic        VARCHAR(255) NOT NULL DEFAULT '',
    level        VARCHAR(255) NOT NULL DEFAULT 1,
    game_mode    VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);