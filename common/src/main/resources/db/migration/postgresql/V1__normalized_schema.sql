CREATE TABLE IF NOT EXISTS ae_group (
  name VARCHAR(64) PRIMARY KEY,
  weight INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ae_user (
  uuid UUID PRIMARY KEY,
  name VARCHAR(16),
  primary_group VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ae_history (
  id BIGSERIAL PRIMARY KEY,
  at TIMESTAMPTZ NOT NULL,
  actor VARCHAR(64) NOT NULL,
  source VARCHAR(16) NOT NULL,
  action VARCHAR(64) NOT NULL,
  target VARCHAR(128) NOT NULL,
  detail TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ae_user_group (
  user_uuid UUID NOT NULL REFERENCES ae_user (uuid) ON DELETE CASCADE,
  group_name VARCHAR(64) NOT NULL REFERENCES ae_group (name) ON DELETE CASCADE,
  PRIMARY KEY (user_uuid, group_name)
);

CREATE TABLE IF NOT EXISTS ae_user_temp_group (
  user_uuid UUID NOT NULL REFERENCES ae_user (uuid) ON DELETE CASCADE,
  group_name VARCHAR(64) NOT NULL REFERENCES ae_group (name) ON DELETE CASCADE,
  expiry TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_uuid, group_name)
);

CREATE TABLE IF NOT EXISTS ae_group_parent (
  group_name VARCHAR(64) NOT NULL REFERENCES ae_group (name) ON DELETE CASCADE,
  parent_name VARCHAR(64) NOT NULL REFERENCES ae_group (name) ON DELETE CASCADE,
  PRIMARY KEY (group_name, parent_name)
);

CREATE TABLE IF NOT EXISTS ae_user_node (
  id BIGSERIAL PRIMARY KEY,
  user_uuid UUID NOT NULL REFERENCES ae_user (uuid) ON DELETE CASCADE,
  permission VARCHAR(256) NOT NULL,
  value BOOLEAN NOT NULL,
  expiry TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS ae_group_node (
  id BIGSERIAL PRIMARY KEY,
  group_name VARCHAR(64) NOT NULL REFERENCES ae_group (name) ON DELETE CASCADE,
  permission VARCHAR(256) NOT NULL,
  value BOOLEAN NOT NULL,
  expiry TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS ae_user_node_context (
  node_id BIGINT NOT NULL REFERENCES ae_user_node (id) ON DELETE CASCADE,
  ctx_key VARCHAR(64) NOT NULL,
  ctx_value VARCHAR(64) NOT NULL,
  PRIMARY KEY (node_id, ctx_key)
);

CREATE TABLE IF NOT EXISTS ae_group_node_context (
  node_id BIGINT NOT NULL REFERENCES ae_group_node (id) ON DELETE CASCADE,
  ctx_key VARCHAR(64) NOT NULL,
  ctx_value VARCHAR(64) NOT NULL,
  PRIMARY KEY (node_id, ctx_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS ae_user_name_lower_uq ON ae_user (LOWER(name));
CREATE INDEX IF NOT EXISTS ae_user_name_lower_prefix_idx ON ae_user (LOWER(name) varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS ae_user_node_user_perm_idx ON ae_user_node (user_uuid, permission);
CREATE INDEX IF NOT EXISTS ae_group_node_group_perm_idx ON ae_group_node (group_name, permission);
CREATE INDEX IF NOT EXISTS ae_history_target_idx ON ae_history (target, at);
