-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--      http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- Prepares the database of a Firebase SQL service for CloudSqlEndToEndTest.
--
-- run_cloud_tests.sh applies this file with psql, through the Cloud SQL Auth Proxy, as a user that
-- has the owner role of the database (`firebase dataconnect:sql:grant -R owner -E <email>`), and
-- sets the psql variable `owner_role` to the name of that role, `firebaseowner_<database>_public`.
--
-- Firebase SQL sends the requests as the `anon`, `authenticated`, and `service_role` roles, which
-- are members of the writer role. The default privileges of the database give the writer and reader
-- roles access to what the owner role creates in `public`, so everything is created as the owner
-- role. The owner role cannot create schemas, so the `inventory` schema of the ignored
-- testMultipleSchemas is not created here. Every statement is idempotent.
\set ON_ERROR_STOP on
SET ROLE :"owner_role";
\ir e2e_schema.sql
-- dryRunDoesNotKeepTheInsertedRow and customPreferHeaderIsMergedWithTheComputedPreferences need
-- PostgREST to apply `Prefer: tx=rollback`. Firebase SQL commits by default (db-tx-end = commit,
-- the upstream default) and reads in-database settings from postgrest.pre_config(), which its SQL
-- setup creates as a no-op that belongs to the owner role. testExplain needs execution plans, which
-- are off by default (db-plan-enabled = false). Plans show details of the database, so only test
-- databases should turn them on.
DO $do$
BEGIN
  CREATE OR REPLACE FUNCTION postgrest.pre_config() RETURNS void LANGUAGE plpgsql AS $fn$
  BEGIN
    PERFORM set_config('pgrst.db_tx_end', 'commit-allow-override', true);
    PERFORM set_config('pgrst.db_plan_enabled', 'true', true);
  END;
  $fn$;
EXCEPTION
  -- A database that Firebase SQL set up before it gave postgrest.pre_config() to the owner role.
  WHEN insufficient_privilege OR invalid_schema_name THEN
    RAISE WARNING 'Could not change postgrest.pre_config(): %', SQLERRM
      USING HINT = 'The dry-run and explain tests will fail. postgrest.pre_config() must '
        || 'belong to ' || current_user || '; ask the Firebase SQL team to change its owner.';
END
$do$;
-- Makes Firebase SQL read its configuration and the schema again right away.
NOTIFY pgrst, 'reload config';
NOTIFY pgrst, 'reload schema';