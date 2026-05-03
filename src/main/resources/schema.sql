CREATE TABLE IF NOT EXISTS analysis_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       VARCHAR(64)   NOT NULL UNIQUE,
    repo_url      VARCHAR(512)  NOT NULL,
    user_id       BIGINT,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    report        TEXT,
    error_message VARCHAR(1024),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
);

CREATE TABLE IF NOT EXISTS trending_repo (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    repo_name     VARCHAR(256)  NOT NULL,
    repo_url      VARCHAR(512)  NOT NULL,
    description   VARCHAR(1024),
    language      VARCHAR(64),
    stars         VARCHAR(32),
    today_stars   VARCHAR(32),
    forks         VARCHAR(32),
    analysis_zh   TEXT,
    analysis_en   TEXT,
    trend_date    DATE          NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trend_date (trend_date)
);

CREATE TABLE IF NOT EXISTS chat_history (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    VARCHAR(64)   NOT NULL,
    user_id       BIGINT,
    repo_url      VARCHAR(512)  NOT NULL,
    role          VARCHAR(16)   NOT NULL,
    content       TEXT          NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user (user_id)
);

CREATE TABLE IF NOT EXISTS email_subscriber (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(256)  NOT NULL UNIQUE,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    language      VARCHAR(8)    NOT NULL DEFAULT 'zh',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS code_chunk (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       VARCHAR(64)   NOT NULL,
    file_path     VARCHAR(1024) NOT NULL,
    start_line    INT           NOT NULL,
    end_line      INT           NOT NULL,
    symbol_name   VARCHAR(256),
    category      VARCHAR(32),
    content_hash  VARCHAR(64)   NOT NULL,
    chunk_index   INT           NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (task_id),
    INDEX idx_task_category (task_id, category)
);

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)   NOT NULL UNIQUE,
    password_hash   VARCHAR(256)  NOT NULL,
    nickname        VARCHAR(128),
    github_username VARCHAR(128),
    avatar_url      VARCHAR(512),
    email           VARCHAR(256),
    email_verified  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email)
);

CREATE TABLE IF NOT EXISTS sys_setting (
    `key`         VARCHAR(128)  NOT NULL PRIMARY KEY,
    `value`       TEXT,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
