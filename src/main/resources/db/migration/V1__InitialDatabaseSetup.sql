-- Items

CREATE SEQUENCE IF NOT EXISTS items_seq START 1 CACHE 8;

CREATE TABLE items (
  id BIGINT PRIMARY KEY NOT NULL,
  name VARCHAR(255) NOT NULL,
  price_in_cents INTEGER NOT NULL,
  category VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  constraint uniq_name unique (name)
);

CREATE INDEX items_category_idx ON items (category);

-- Stock entries

CREATE SEQUENCE IF NOT EXISTS entries_seq START 1 CACHE 512;

CREATE TABLE entries (
  id BIGINT PRIMARY KEY NOT NULL,
  item_id BIGINT NOT NULL,
  delta INTEGER NOT NULL,
  timestamp TIMESTAMP NOT NULL,
  CONSTRAINT fk_entries_items
     FOREIGN KEY (item_id)
     REFERENCES items (id) ON DELETE CASCADE
);

CREATE INDEX entries_items_idx ON entries (item_id);
