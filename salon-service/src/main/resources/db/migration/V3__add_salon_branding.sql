ALTER TABLE salons
    ADD COLUMN logo_url       VARCHAR(500)  NULL AFTER description,
    ADD COLUMN primary_color  VARCHAR(7)    NULL AFTER logo_url;
