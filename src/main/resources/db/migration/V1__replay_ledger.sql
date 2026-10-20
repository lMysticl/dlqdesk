CREATE TABLE replay_run (
  id VARCHAR(36) PRIMARY KEY,
  state VARCHAR(32) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  source_topic VARCHAR(249) NOT NULL,
  target_topic VARCHAR(249) NOT NULL,
  replay_rate INTEGER NOT NULL CHECK (replay_rate BETWEEN 1 AND 100)
);

CREATE TABLE replay_entry (
  run_id VARCHAR(36) NOT NULL REFERENCES replay_run(id),
  entry_index INTEGER NOT NULL,
  source_partition INTEGER NOT NULL,
  source_offset VARCHAR(20) NOT NULL,
  key_bytes CLOB,
  original_payload CLOB,
  output_payload CLOB NOT NULL,
  headers_json CLOB NOT NULL,
  source_hash VARCHAR(64) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  edited BOOLEAN NOT NULL,
  state VARCHAR(24) NOT NULL,
  target_partition INTEGER,
  target_offset VARCHAR(20),
  error_detail VARCHAR(2000),
  PRIMARY KEY (run_id, entry_index),
  UNIQUE (run_id, source_partition, source_offset)
);

CREATE TABLE replay_audit (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  run_id VARCHAR(36) NOT NULL REFERENCES replay_run(id),
  created_at VARCHAR(40) NOT NULL,
  action VARCHAR(40) NOT NULL,
  detail VARCHAR(1000) NOT NULL
);
CREATE INDEX replay_entry_state ON replay_entry(run_id, state);
CREATE INDEX replay_audit_run ON replay_audit(run_id, id);
