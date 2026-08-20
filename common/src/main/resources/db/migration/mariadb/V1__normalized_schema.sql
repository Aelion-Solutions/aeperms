CREATE TABLE IF NOT EXISTS ae_group (
  name VARCHAR(64) PRIMARY KEY,
  weight INT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT NOW()
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_user (
  uuid CHAR(36) PRIMARY KEY,
  name VARCHAR(16),
  name_lower VARCHAR(16) AS (LOWER(name)) VIRTUAL,
  primary_group VARCHAR(64),
  created_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
  UNIQUE INDEX ae_user_name_lower_uq (name_lower)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  at TIMESTAMP(6) NOT NULL,
  actor VARCHAR(64) NOT NULL,
  source VARCHAR(16) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target VARCHAR(128) NOT NULL,
  detail TEXT NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_user_group (
  user_uuid CHAR(36) NOT NULL,
  group_name VARCHAR(64) NOT NULL,
  PRIMARY KEY (user_uuid, group_name),
  CONSTRAINT ae_user_group_user_fk FOREIGN KEY (user_uuid) REFERENCES ae_user (uuid) ON DELETE CASCADE,
  CONSTRAINT ae_user_group_group_fk FOREIGN KEY (group_name) REFERENCES ae_group (name) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_user_temp_group (
  user_uuid CHAR(36) NOT NULL,
  group_name VARCHAR(64) NOT NULL,
  expiry TIMESTAMP(6) NOT NULL,
  PRIMARY KEY (user_uuid, group_name),
  CONSTRAINT ae_user_temp_group_user_fk FOREIGN KEY (user_uuid) REFERENCES ae_user (uuid) ON DELETE CASCADE,
  CONSTRAINT ae_user_temp_group_group_fk FOREIGN KEY (group_name) REFERENCES ae_group (name) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_group_parent (
  group_name VARCHAR(64) NOT NULL,
  parent_name VARCHAR(64) NOT NULL,
  PRIMARY KEY (group_name, parent_name),
  CONSTRAINT ae_group_parent_child_fk FOREIGN KEY (group_name) REFERENCES ae_group (name) ON DELETE CASCADE,
  CONSTRAINT ae_group_parent_parent_fk FOREIGN KEY (parent_name) REFERENCES ae_group (name) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_user_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_uuid CHAR(36) NOT NULL,
  permission VARCHAR(256) NOT NULL,
  value TINYINT(1) NOT NULL,
  expiry TIMESTAMP(6) NULL,
  CONSTRAINT ae_user_node_user_fk FOREIGN KEY (user_uuid) REFERENCES ae_user (uuid) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_group_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_name VARCHAR(64) NOT NULL,
  permission VARCHAR(256) NOT NULL,
  value TINYINT(1) NOT NULL,
  expiry TIMESTAMP(6) NULL,
  CONSTRAINT ae_group_node_group_fk FOREIGN KEY (group_name) REFERENCES ae_group (name) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_user_node_context (
  node_id BIGINT NOT NULL,
  ctx_key VARCHAR(64) NOT NULL,
  ctx_value VARCHAR(64) NOT NULL,
  PRIMARY KEY (node_id, ctx_key),
  CONSTRAINT ae_user_node_context_fk FOREIGN KEY (node_id) REFERENCES ae_user_node (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ae_group_node_context (
  node_id BIGINT NOT NULL,
  ctx_key VARCHAR(64) NOT NULL,
  ctx_value VARCHAR(64) NOT NULL,
  PRIMARY KEY (node_id, ctx_key),
  CONSTRAINT ae_group_node_context_fk FOREIGN KEY (node_id) REFERENCES ae_group_node (id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX IF NOT EXISTS ae_user_node_user_perm_idx ON ae_user_node (user_uuid, permission);
CREATE INDEX IF NOT EXISTS ae_group_node_group_perm_idx ON ae_group_node (group_name, permission);
CREATE INDEX IF NOT EXISTS ae_history_target_idx ON ae_history (target, at);
