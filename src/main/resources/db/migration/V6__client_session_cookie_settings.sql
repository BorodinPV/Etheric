ALTER TABLE clients
    ADD COLUMN session_cookie_name TEXT,
    ADD COLUMN session_cookie_secure BOOLEAN;
