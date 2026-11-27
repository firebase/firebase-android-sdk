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
-- Firebase SQL sends the requests as the `anon`, `authenticated`, and `service_role` roles. The
-- default privileges of the database give the writer and reader roles access to what the owner role
-- creates in `public`, so everything is created as the owner role, and e2e_schema.sql grants the
-- tables to `anon`, the role of the tests. The owner role cannot create schemas, so the `inventory`
-- schema of the ignored testMultipleSchemas is not created here. Every statement is idempotent.
--
-- The setup lets requests without a token read and change the test tables, and replaces
-- postgrest.pre_config(), so it must change only a database that is for these tests. It refuses a
-- database whose test tables do not have the comment 'firebase-sql-e2e', which e2e_schema.sql gives
-- them, and a database that has other tables but not the test tables. For a database that is only
-- for these tests, but whose test tables have no comment yet, set the psql variable
-- `adopt_test_tables` to true once.
\set ON_ERROR_STOP on
SET ROLE :"owner_role";
\if :{?adopt_test_tables}
\else
\set adopt_test_tables false
\endif
\if :adopt_test_tables
\echo 'adopt_test_tables is true, so the database is not checked.'
\else
DO $guard$
DECLARE
  test_table text;
BEGIN
  IF to_regclass('public.categories') IS NULL AND to_regclass('public.products') IS NULL THEN
    IF EXISTS (
      SELECT FROM pg_catalog.pg_class c
      JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
    ) THEN
      RAISE EXCEPTION 'The database has tables, but not the test tables, so it is in use'
        USING HINT = 'Run the tests against a Firebase SQL service that you use only for them.';
    END IF;
  ELSE
    FOREACH test_table IN ARRAY ARRAY['public.categories', 'public.products'] LOOP
      IF to_regclass(test_table) IS NULL THEN
        RAISE EXCEPTION 'The database has only one of the test tables: % is missing', test_table
          USING HINT = 'Run the tests against a Firebase SQL service that you use only for them.';
      END IF;
      IF obj_description(to_regclass(test_table), 'pg_class') IS DISTINCT FROM 'firebase-sql-e2e'
      THEN
        RAISE EXCEPTION '% is not a test table: it does not have the comment ''firebase-sql-e2e''',
          test_table
          USING HINT = 'Run the tests against a Firebase SQL service that you use only for them. '
            || 'If this database is only for these tests, set adopt_test_tables to true once '
            || '(ADOPT_TEST_TABLES=true for run_cloud_tests.sh).';
      END IF;
    END LOOP;
  END IF;
END
$guard$;
\endif
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