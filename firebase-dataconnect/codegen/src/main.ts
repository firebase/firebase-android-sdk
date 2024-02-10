/**
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as fs from 'node:fs';

import * as graphql from 'graphql';

const GRAPHQL_SCHEMA_FILE =
  '/home/dconeybe/dev/firebase/android/firebase-dataconnect/src/androidTest' +
  '/assets/testing_graphql_schemas/person/schema.gql';
const GRAPHQL_OPS_FILE =
  '/home/dconeybe/dev/firebase/android/firebase-dataconnect/src/androidTest' +
  '/assets/testing_graphql_schemas/person/ops.gql';

function main() {
  const types = parseGraphQLTypes();
  parseGraphQLOperations();
}

function parseGraphQLTypes(): Map<string, graphql.ObjectTypeDefinitionNode> {
  const parsedFile = parseGraphQLFile(GRAPHQL_SCHEMA_FILE);

  const types = new Map<string, graphql.ObjectTypeDefinitionNode>();

  for (const definition of parsedFile.definitions) {
    if (definition.kind === graphql.Kind.OBJECT_TYPE_DEFINITION) {
      if (hasDirective(definition, 'table')) {
        const typeName = definition.name.value;
        if (types.has(typeName)) {
          throw new DuplicateGraphQLTypeDefinitionError(
            `type defined more than once: ${typeName}`
          );
        }
        types.set(typeName, definition);
      }
    } else {
      throw new UnsupportedGraphQLDefinitionKindError(
        `unsupported GraphQL definition kind ` +
          `at ${displayStringFromLocation(definition.loc)}: ${definition.kind}`
      );
    }
  }

  return types;
}

function parseGraphQLOperations() {
  const parsedFile = parseGraphQLFile(GRAPHQL_OPS_FILE);

  const types = new Map<string, graphql.ObjectTypeDefinitionNode>();

  for (const definition of parsedFile.definitions) {
    if (definition.kind === graphql.Kind.OPERATION_DEFINITION) {
    } else {
      throw new UnsupportedGraphQLDefinitionKindError(
        `unsupported GraphQL definition kind ` +
          `at ${displayStringFromLocation(definition.loc)}: ${definition.kind}`
      );
    }
  }

  return types;
}

function parseGraphQLFile(path: string): graphql.DocumentNode {
  console.log('Parsing file:', path);
  const body = fs.readFileSync(path, { encoding: 'utf-8' });
  const source = new graphql.Source(body, path);
  return graphql.parse(source);
}

function displayStringFromLocation(
  location: graphql.Location | undefined
): string {
  if (!location) {
    return '[unknown location]';
  }
  const { line, column } = location.source.locationOffset;
  return `${line}:${column}`;
}

function hasDirective(
  node: graphql.ObjectTypeDefinitionNode,
  directiveName: string
): boolean {
  if (node.directives) {
    for (const directive of node.directives) {
      if (directive.name.value === directiveName) {
        return true;
      }
    }
  }
  return false;
}

class UnsupportedGraphQLDefinitionKindError extends Error {
  constructor(message: string) {
    super(message);
  }
}

class DuplicateGraphQLTypeDefinitionError extends Error {
  constructor(message: string) {
    super(message);
  }
}

main();
