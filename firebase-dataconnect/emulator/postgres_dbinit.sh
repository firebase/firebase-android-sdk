#!/bin/sh

# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script is mounted in the postgresql container such that it will be run
# on a fresh instance. Specifically, this will create the database that that the
# dataconnect emulator expects to exist.
# See https://hub.docker.com/_/postgres for details, especially the
# "Initialization Scripts" section.

set -xev

echo "POSTGRES_USER=$POSTGRES_USER"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
	CREATE DATABASE emulator;
	GRANT ALL PRIVILEGES ON DATABASE emulator TO $POSTGRES_USER;
EOSQL
