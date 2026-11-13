-- Allow 'QUOTE' lesson page blocks for highlighted literary or source quotations.

ALTER TABLE page_block DROP CONSTRAINT ck_page_block_type;
ALTER TABLE page_block ADD CONSTRAINT ck_page_block_type CHECK (
    block_type IN ('PARAGRAPH', 'EXAMPLE', 'TIP', 'NOTE', 'WARNING', 'QUOTE'));
