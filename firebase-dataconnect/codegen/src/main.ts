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

import * as child_process from 'node:child_process';
import * as fs from 'node:fs';

import * as graphql from 'graphql';
import { OperationTypeNode } from 'graphql';
import * as which from 'which';

const GRAPHQL_SCHEMA_FILE =
  '/home/dconeybe/dev/firebase/android/firebase-dataconnect/src/androidTest' +
  '/assets/testing_graphql_schemas/person/schema.gql';
const GRAPHQL_OPS_FILE =
  '/home/dconeybe/dev/firebase/android/firebase-dataconnect/src/androidTest' +
  '/assets/testing_graphql_schemas/person/ops.gql';
const OUTPUT_BASE_DIR =
  '/home/dconeybe/dev/firebase/android/firebase-dataconnect/src/main/' +
  'kotlin/com/google/firebase/dataconnect/connectors';
const CONNECTOR_NAME = 'crud';
const KOTLIN_BASE_PACKAGE = 'com.google.firebase.dataconnect.connectors';

async function main(): Promise<void> {
  const types = parseGraphQLTypes();
  const operations = parseGraphQLOperations();
  await generateOperationsKtSources(operations, types);
}

async function generateOperationsKtSources(
  operations: Map<string, graphql.OperationDefinitionNode>,
  types: Map<string, graphql.ObjectTypeDefinitionNode>
): Promise<void> {
  const operationNamesSorted = Array.from(operations.keys()).sort();
  for (const operationName of operationNamesSorted) {
    const operation = operations.get(operationName)!;
    await generateOperationKtSource(operationName, operation, types);
  }
}

function* tomlConfigLines(config: {
  kotlinPackage: string;
  operationName: string;
  operationType: 'query' | 'mutation';
  variables: string[];
}): Generator<string> {
  yield `kotlinPackage = '${config.kotlinPackage}'`;
  yield `operationName = '${config.operationName}'`;
  yield `operationType = '${config.operationType}'`;

  for (const variable of config.variables) {
    yield `[variables.${variable}]`;
    yield "type = 'string'";
  }
}

async function generateOperationKtSource(
  operationName: string,
  operation: graphql.OperationDefinitionNode,
  types: Map<string, graphql.ObjectTypeDefinitionNode>
): Promise<void> {
  const outputDir = `${OUTPUT_BASE_DIR}/${CONNECTOR_NAME}`;
  const outputFile = `${outputDir}/${operationName}.kt`;
  fs.mkdirSync(outputDir, { recursive: true });

  const templateFile = `${__dirname}/operation.template.txt`;
  const goAppDir = `${__dirname}/go_template_processor`;
  const goExecutable = which.sync('go');

  const variables: string[] = [];
  if (operation.variableDefinitions) {
    for (const variableDefinition of operation.variableDefinitions) {
      variables.push(variableDefinition.variable.name.value);
    }
  }

  let operationType: 'query' | 'mutation';
  if (operation.operation === OperationTypeNode.QUERY) {
    operationType = 'query';
  } else if (operation.operation === OperationTypeNode.MUTATION) {
    operationType = 'mutation';
  } else {
    throw new UnsupportedGraphQLOperationTypeError(
      `unsupported GraphQL operation type  ` +
        `at ${displayStringFromLocation(operation.loc)}: ${operation.operation}`
    );
  }

  const tempy = await import('tempy');
  const tomlLines = Array.from(
    tomlConfigLines({
      kotlinPackage: `com.google.firebase.dataconnect.connectors.${CONNECTOR_NAME}`,
      operationName,
      operationType,
      variables
    })
  );
  const tomlFile = tempy.temporaryWriteSync(tomlLines.join('\n'));
  try {
    const args = [
      goExecutable,
      'run',
      '-C',
      goAppDir,
      '.',
      '--',
      tomlFile,
      templateFile,
      outputFile
    ];
    console.log(`Running command: ${args.join(' ')}`);
    const spawnResult = child_process.spawnSync(args[0], args.slice(1), {
      stdio: 'inherit'
    });
    if (spawnResult.error) {
      throw spawnResult.error;
    } else if (spawnResult.status !== 0) {
      throw new Error(
        `command completed with non-zero exit code ${spawnResult.status}: ` +
          args.join(' ')
      );
    }
  } finally {
    fs.unlinkSync(tomlFile);
  }
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

function parseGraphQLOperations(): Map<
  string,
  graphql.OperationDefinitionNode
> {
  const parsedFile = parseGraphQLFile(GRAPHQL_OPS_FILE);

  const operations = new Map<string, graphql.OperationDefinitionNode>();

  for (const definition of parsedFile.definitions) {
    if (definition.kind === graphql.Kind.OPERATION_DEFINITION) {
      const operationName = definition.name?.value;
      if (!operationName) {
        continue;
      }
      if (operations.has(operationName)) {
        throw new DuplicateGraphQLOperationDefinitionError(
          `operation defined more than once: ${operationName}`
        );
      }
      operations.set(operationName, definition);
    } else {
      throw new UnsupportedGraphQLDefinitionKindError(
        `unsupported GraphQL definition kind ` +
          `at ${displayStringFromLocation(definition.loc)}: ${definition.kind}`
      );
    }
  }

  return operations;
}

function parseGraphQLFile(path: string): graphql.DocumentNode {
  console.log(`Parsing ${path}`);
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

class UnsupportedGraphQLOperationTypeError extends Error {
  constructor(message: string) {
    super(message);
  }
}

class DuplicateGraphQLTypeDefinitionError extends Error {
  constructor(message: string) {
    super(message);
  }
}

class DuplicateGraphQLOperationDefinitionError extends Error {
  constructor(message: string) {
    super(message);
  }
}

main();
