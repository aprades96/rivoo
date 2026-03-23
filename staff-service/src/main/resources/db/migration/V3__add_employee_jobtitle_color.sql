ALTER TABLE employees
    ADD COLUMN job_title  VARCHAR(100)  NULL AFTER phone,
    ADD COLUMN color_hex  VARCHAR(7)    DEFAULT '#3B82F6' AFTER job_title;
