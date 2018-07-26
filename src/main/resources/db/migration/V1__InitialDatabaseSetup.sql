-- Items

CREATE TABLE items (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  price_in_cents INTEGER NOT NULL,
  category VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  constraint uniq_name unique (name)
);

CREATE INDEX items_category_idx ON items (category);

-- Stock entries

CREATE TABLE entries (
  id SERIAL PRIMARY KEY,
  item_id INTEGER NOT NULL,
  delta INTEGER NOT NULL,
  timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  constraint fk_entries_items
     FOREIGN KEY (item_id)
     REFERENCES items (id) ON DELETE CASCADE
);

