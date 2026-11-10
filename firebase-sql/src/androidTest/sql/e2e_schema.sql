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

-- Schema of the database that CloudSqlEndToEndTest runs against.
--
-- run_cloud_tests.sh applies this file through e2e_setup.sql. Every statement is idempotent,
-- so the file can be applied again to a database that already has the schema; it never drops or
-- deletes anything. A table that already exists is not changed, so a change to a table here needs a
-- migration of the existing database too.
--
-- Everything is in the `public` schema, the schema of the Firebase SQL service.

CREATE TABLE IF NOT EXISTS public.categories (
  id bigint PRIMARY KEY,
  name text
);

-- Columns for each kind of filter: arrays (cs, cd, ov, and quantified like), ranges (sl, sr, nxl,
-- nxr, adj, ov), and full-text search (fts, plfts, phfts, wfts).
CREATE TABLE IF NOT EXISTS public.products (
  id bigint PRIMARY KEY,
  name text,
  description text,
  is_active boolean,
  category_id bigint REFERENCES public.categories (id),
  tags text[],
  price_range int4range,
  search_vector tsvector
);

CREATE OR REPLACE FUNCTION public.hello_world() RETURNS text
  LANGUAGE plpgsql
  AS $$
BEGIN
  RETURN 'Hello from Cloud SQL!';
END;
$$;

-- Returns its argument unchanged, to check how the SDK sends the arguments of a function.
CREATE OR REPLACE FUNCTION public.echo_text(input_text text) RETURNS text
  LANGUAGE plpgsql
  AS $$
BEGIN
  RETURN input_text;
END;
$$;

