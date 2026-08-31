-- Add OAuth token endpoint authentication method per client.
-- Two supported values (RFC 6749 §2.3):
--   client_secret_basic — confidential client; the client must authenticate with its secret
--   none               — public client; authenticated via PKCE, no secret from the client
-- Default for existing rows is client_secret_basic (the safe choice).

ALTER TABLE clients
    ADD COLUMN token_endpoint_auth_method TEXT;

UPDATE clients
    SET token_endpoint_auth_method = 'client_secret_basic'
    WHERE token_endpoint_auth_method IS NULL;

ALTER TABLE clients
    ALTER COLUMN token_endpoint_auth_method SET NOT NULL,
    ALTER COLUMN token_endpoint_auth_method SET DEFAULT 'client_secret_basic';

ALTER TABLE clients
    ADD CONSTRAINT clients_token_endpoint_auth_method_check
    CHECK (token_endpoint_auth_method IN ('client_secret_basic', 'none'));
