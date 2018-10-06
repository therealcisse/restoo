-- Add currency field to item's table

ALTER TABLE items
ADD COLUMN currency VARCHAR NOT NULL;