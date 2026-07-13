CREATE TABLE IF NOT EXISTS user_verifiable_credentials (
    user_id text,
    client_scope_id text,
    id text,
    revision text,
    user_attributes map<text, frozen<list<text>>>,
    created_date bigint,
    updated_date bigint,
    PRIMARY KEY (user_id, client_scope_id)
);

CREATE TABLE IF NOT EXISTS user_verifiable_credentials_by_id (
    id text PRIMARY KEY,
    user_id text,
    client_scope_id text
);

CREATE TABLE IF NOT EXISTS issued_verifiable_credentials (
    user_id text,
    id text,
    verifiable_credential_id text,
    issued_at bigint,
    expires_at bigint,
    client_id text,
    revision text,
    PRIMARY KEY (user_id, id)
);

CREATE TABLE IF NOT EXISTS issued_verifiable_credentials_by_id (
    id text PRIMARY KEY,
    user_id text
);

CREATE TABLE IF NOT EXISTS deployment_state (
    id text PRIMARY KEY,
    resources_version_seed text
);
