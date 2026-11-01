-- Run once in the Supabase SQL editor before starting the applications.
-- Supabase owns the reserved auth and storage schemas, so application schemas
-- use the sma_ prefix. The script is idempotent.

do $$
declare
    role_name text;
begin
    foreach role_name in array array[
        'sma_auth_service', 'sma_user_service', 'sma_media_service',
        'sma_post_service', 'sma_comment_service', 'sma_reaction_service',
        'sma_group_service', 'sma_fanpage_service', 'sma_dating_service',
        'sma_moderation_service'
    ] loop
        if not exists (select 1 from pg_roles where rolname = role_name) then
            execute format('create role %I nologin noinherit', role_name);
        end if;
    end loop;
end
$$;

-- Grant membership before CREATE SCHEMA AUTHORIZATION: the Supabase postgres
-- role is not an unrestricted superuser and must be able to SET ROLE to owners.
-- Membership also lets it create Hibernate-managed tables with default credentials.
grant sma_auth_service, sma_user_service, sma_media_service, sma_post_service,
    sma_comment_service, sma_reaction_service, sma_group_service,
    sma_fanpage_service, sma_dating_service, sma_moderation_service to postgres;

create schema if not exists sma_auth authorization sma_auth_service;
create schema if not exists sma_user authorization sma_user_service;
create schema if not exists sma_media authorization sma_media_service;
create schema if not exists sma_post authorization sma_post_service;
create schema if not exists sma_comment authorization sma_comment_service;
create schema if not exists sma_reaction authorization sma_reaction_service;
create schema if not exists sma_group authorization sma_group_service;
create schema if not exists sma_fanpage authorization sma_fanpage_service;
create schema if not exists sma_dating authorization sma_dating_service;
create schema if not exists sma_moderation authorization sma_moderation_service;

revoke all on schema sma_auth, sma_user, sma_media, sma_post, sma_comment,
    sma_reaction, sma_group, sma_fanpage, sma_dating, sma_moderation
    from public, anon, authenticated;

grant usage, create on schema sma_auth to sma_auth_service;
grant usage, create on schema sma_user to sma_user_service;
grant usage, create on schema sma_media to sma_media_service;
grant usage, create on schema sma_post to sma_post_service;
grant usage, create on schema sma_comment to sma_comment_service;
grant usage, create on schema sma_reaction to sma_reaction_service;
grant usage, create on schema sma_group to sma_group_service;
grant usage, create on schema sma_fanpage to sma_fanpage_service;
grant usage, create on schema sma_dating to sma_dating_service;
grant usage, create on schema sma_moderation to sma_moderation_service;

alter role sma_auth_service set search_path = sma_auth, public;
alter role sma_user_service set search_path = sma_user, public;
alter role sma_media_service set search_path = sma_media, public;
alter role sma_post_service set search_path = sma_post, public;
alter role sma_comment_service set search_path = sma_comment, public;
alter role sma_reaction_service set search_path = sma_reaction, public;
alter role sma_group_service set search_path = sma_group, public;
alter role sma_fanpage_service set search_path = sma_fanpage, public;
alter role sma_dating_service set search_path = sma_dating, public;
alter role sma_moderation_service set search_path = sma_moderation, public;

-- Optional strict isolation: assign a unique password and LOGIN to each role,
-- then fill its SUPABASE_<SERVICE>_USER/PASSWORD values in .runtime/env.ps1.
-- Example only; never commit the real password:
-- alter role sma_auth_service login password '<strong-unique-password>';
