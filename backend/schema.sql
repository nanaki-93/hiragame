CREATE EXTENSION IF NOT EXISTS "uuid-ossp";


DROP TABLE IF EXISTS jp_user CASCADE;
CREATE TABLE if not exists jp_user
(
    id         uuid PRIMARY KEY   DEFAULT uuid_generate_v4(),
    username   text      NOT NULL UNIQUE,
    password   text      NOT NULL,
    refresh_token text,
    expires_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);


DROP TABLE IF EXISTS user_answered_question CASCADE;
CREATE TABLE user_answered_question
(
    id                uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           uuid    NOT NULL REFERENCES jp_user (id) ON DELETE CASCADE,
    question_id       uuid    NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    is_correct        boolean NOT NULL,
    answered_at       timestamp,
    attemps           integer,
    last_attempted_at timestamp,
    game_mode         text    NOT NULL,
    level             text    not null,
    UNIQUE (user_id, question_id, game_mode)
);

DROP TABLE IF EXISTS user_level CASCADE;
CREATE TABLE if not exists user_level
(
    id            uuid PRIMARY KEY   DEFAULT uuid_generate_v4(),
    user_id       uuid      NOT NULL REFERENCES jp_user (id) ON DELETE CASCADE,
    level         text      NOT NULL,
    is_completed  boolean   NOT NULL,
    is_available  boolean   NOT NULL,
    answered_at   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    game_mode     text      NOT NULL,
    correct_count integer,
    UNIQUE (user_id, level, game_mode)
);

DROP TABLE IF EXISTS user_game_state CASCADE;
CREATE TABLE if not exists user_game_state
(
    id                  uuid PRIMARY KEY   DEFAULT uuid_generate_v4(),
    user_id             uuid      NOT NULL REFERENCES jp_user (id) ON DELETE CASCADE,
    game_mode           text      NOT NULL,
    level               text      NOT NULL DEFAULT 'N5',
    score               integer   NOT NULL DEFAULT 0,
    streak              integer   NOT NULL DEFAULT 0,
    total_answered      integer   NOT NULL DEFAULT 0,
    correct_answers     integer   NOT NULL DEFAULT 0,
    last_answer_correct boolean,
    created_at          timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, game_mode)
);


-- Indexes for performance
CREATE INDEX idx_user_answered_questions_user_id ON user_answered_question (user_id);
CREATE INDEX idx_user_answered_questions_question_id ON user_answered_question (question_id);
CREATE INDEX idx_user_answered_questions_answered_at ON user_answered_question (answered_at);
CREATE INDEX idx_user_answered_questions_game_mode ON user_answered_question (game_mode);
