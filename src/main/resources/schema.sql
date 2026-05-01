CREATE TABLE IF NOT EXISTS analysis_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id       VARCHAR(64)   NOT NULL UNIQUE,
    repo_url      VARCHAR(512)  NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    report        TEXT,
    error_message VARCHAR(1024),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    trend_date    DATE          NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
