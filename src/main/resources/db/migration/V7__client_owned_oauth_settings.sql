-- Move OAuth/token settings from server_settings to each client, then drop global table.

UPDATE clients SET
    access_token_lifetime_seconds = COALESCE(
        access_token_lifetime_seconds,
        (SELECT default_access_token_lifetime_seconds FROM server_settings WHERE id = 1),
        3600),
    refresh_token_lifetime_seconds = COALESCE(
        refresh_token_lifetime_seconds,
        (SELECT default_refresh_token_lifetime_seconds FROM server_settings WHERE id = 1),
        604800),
    session_lifetime_seconds = COALESCE(
        session_lifetime_seconds,
        (SELECT oauth_session_lifetime_seconds FROM server_settings WHERE id = 1),
        28800),
    session_cookie_name = COALESCE(
        NULLIF(TRIM(session_cookie_name), ''),
        (SELECT oauth_session_cookie_name FROM server_settings WHERE id = 1),
        'SESSIONID'),
    session_cookie_secure = COALESCE(
        session_cookie_secure,
        (SELECT session_cookie_secure FROM server_settings WHERE id = 1),
        TRUE);

ALTER TABLE clients
    ALTER COLUMN access_token_lifetime_seconds SET NOT NULL,
    ALTER COLUMN refresh_token_lifetime_seconds SET NOT NULL,
    ALTER COLUMN session_lifetime_seconds SET NOT NULL,
    ALTER COLUMN session_cookie_name SET NOT NULL,
    ALTER COLUMN session_cookie_secure SET NOT NULL;

DROP TABLE IF EXISTS server_settings;
