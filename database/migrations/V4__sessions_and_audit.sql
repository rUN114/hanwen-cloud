-- Additive migration. Do not remove existing teaching data.
SET NAMES utf8mb4;
USE hw_user;
CREATE TABLE IF NOT EXISTS auth_refresh_session (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL,
 token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 auth_version INT NOT NULL,
 expires_at DATETIME(3) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 updated_at DATETIME(3) NOT NULL,
 UNIQUE KEY uk_refresh_hash(token_hash),
 KEY ix_refresh_user(user_id),
 KEY ix_refresh_expiry(expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS user_audit_event (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT UNSIGNED NOT NULL,
 target_id BIGINT UNSIGNED NULL,
 action VARCHAR(32) NOT NULL,
 summary VARCHAR(200) NOT NULL,
 trace_id VARCHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL,
 KEY ix_audit_created(created_at,id),
 KEY ix_audit_actor(actor_id,id)
) ENGINE=InnoDB;
