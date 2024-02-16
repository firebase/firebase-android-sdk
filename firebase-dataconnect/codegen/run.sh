#!/bin/bash

readonly args=(
  go
  run
  .
  -dest_dir
  ../src/main/kotlin/com/google/firebase/dataconnect/connectors
  ../src/androidTest/assets/testing_graphql_schemas/person/schema.gql
  ../src/androidTest/assets/testing_graphql_schemas/person/ops.gql
)

echo "${args[*]}"
exec "${args[@]}"
