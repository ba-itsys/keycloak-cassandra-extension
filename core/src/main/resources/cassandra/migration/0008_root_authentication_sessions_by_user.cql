CREATE TABLE IF NOT EXISTS root_authentication_sessions_by_user (
    realm_id text,
    user_id text,
    root_session_id text,
    PRIMARY KEY ((realm_id, user_id), root_session_id)
) WITH gc_grace_seconds = 4320;
