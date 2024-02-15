#!/bin/bash

readonly args=(
  go
  run
  .
  -prelude_dir
  prelude
  -dest_dir
  ../src/main/kotlin/com/google/firebase/dataconnect/connectors
  operations.gotmpl
  ../src/androidTest/assets/testing_graphql_schemas/person/schema.gql
  ../src/androidTest/assets/testing_graphql_schemas/person/ops.gql
)

echo "${args[*]}"
exec "${args[@]}"
