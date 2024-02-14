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
import * as which from 'which';
import { OperationTypeNode } from 'graphql/language/ast';

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
  operations: graphql.OperationDefinitionNode[],
  types: Map<string, graphql.ObjectTypeDefinitionNode>
): Promise<void> {
  for (const operation of operations) {
    await generateOperationKtSource(operation, types);
  }
}

interface TypeInfo {
  name: string;
  isList: boolean;
  isNullable: boolean;
}

function typeInfoFromTypeNode(node: graphql.TypeNode): TypeInfo {
  if (node.kind === graphql.Kind.NAMED_TYPE) {
    return { name: node.name.value, isList: false, isNullable: true };
  } else if (node.kind === graphql.Kind.LIST_TYPE) {
    return { ...typeInfoFromTypeNode(node.type), isList: true };
  } else if (node.kind === graphql.Kind.NON_NULL_TYPE) {
    return { ...typeInfoFromTypeNode(node.type), isNullable: false };
  } else {
    throw new Error('internal error: unknown node.kind');
  }
}

function fieldInfoFromVariableDefinition(
  node: graphql.VariableDefinitionNode,
  types: Map<string, graphql.ObjectTypeDefinitionNode>
): FieldInfo {
  const name = node.variable.name.value;
  const typeInfo = typeInfoFromTypeNode(node.type);

  const fields: FieldInfo[] = [];
  if (typeInfo.name.endsWith('_Data')) {
    const typesKey = typeInfo.name.substring(0, typeInfo.name.length - 5);
    const typeInfo2 = types.get(typesKey);
    if (typeInfo2 === undefined) {
      throw new Error(`typesKey not found: ${typesKey}`);
    }
    if (typeInfo2.fields) {
      for (const fieldDefinition of typeInfo2.fields) {
        const fieldName = fieldDefinition.name.value;
        const fieldType = typeInfoFromTypeNode(fieldDefinition.type);
        fields.push({
          name: fieldName,
          type: fieldType.name,
          isList: fieldType.isList,
          isNullable: fieldType.isNullable,
          fields: []
        });
      }
    }
  }

  return {
    name,
    type: typeInfo.name,
    isList: typeInfo.isList,
    isNullable: typeInfo.isNullable,
    fields
  };
}

function flattenedVariablesFrom(
  operation: graphql.OperationDefinitionNode,
  types: Map<string, graphql.ObjectTypeDefinitionNode>
): Map<string, TypeInfo> | null {
  if (!operation.variableDefinitions) {
    return null;
  }

  const flattenedVariables = new Map<string, TypeInfo>();
  const requiredTypeNames: string[] = [];
  for (const variableDefinition of operation.variableDefinitions) {
    const variableName = variableDefinition.variable.name.value;
    if (flattenedVariables.has(variableName)) {
      return null;
    }

    const typeInfo = typeInfoFromTypeNode(variableDefinition.type);
    if (typeInfo.name === 'String' || typeInfo.name === 'Int') {
      flattenedVariables.set(variableName, typeInfo);
    } else {
      requiredTypeNames.push(typeInfo.name);
    }
  }

  for (const typeName of requiredTypeNames) {
    const realTypeName = typeName.endsWith('_Data')
      ? typeName.substring(0, typeName.length - 5)
      : typeName;
    const typeNode = types.get(realTypeName);
    if (!typeNode) {
      throw new Error(`missing type definition: ${realTypeName}`);
    }
    if (!typeNode.fields) {
      continue;
    }

    for (const field of typeNode.fields) {
      const fieldName = field.name.value;
      if (flattenedVariables.has(fieldName)) {
        return null;
      }
      const typeInfo = typeInfoFromTypeNode(field.type);
      if (typeInfo.name === 'String' || typeInfo.name === 'Int') {
        flattenedVariables.set(fieldName, typeInfo);
      } else {
        requiredTypeNames.push(typeInfo.name);
      }
    }
  }

  return flattenedVariables;
}

async function generateOperationKtSource(
  operation: graphql.OperationDefinitionNode,
  types: Map<string, graphql.ObjectTypeDefinitionNode>
): Promise<void> {
  const operationName = operation.name?.value;
  if (operationName === undefined) {
    throw new Error('internal error: operationName === undefined');
  }

  const outputDir = `${OUTPUT_BASE_DIR}/${CONNECTOR_NAME}`;
  const outputFile = `${outputDir}/${operationName}.kt`;
  fs.mkdirSync(outputDir, { recursive: true });

  const templateFile = `${__dirname}/operation.template.txt`;
  const goAppDir = `${__dirname}/go_template_processor`;
  const goExecutable = which.sync('go');

  const configVariables = (operation.variableDefinitions ?? []).map(
    variableDefinition =>
      fieldInfoFromVariableDefinition(variableDefinition, types)
  );

  const flattenedConfigVariables = flattenedVariablesFrom(operation, types);

  let operationType: 'query' | 'mutation';
  if (operation.operation === graphql.OperationTypeNode.QUERY) {
    operationType = 'query';
  } else if (operation.operation === graphql.OperationTypeNode.MUTATION) {
    operationType = 'mutation';
  } else {
    throw new UnsupportedGraphQLOperationTypeError(
      `unsupported graphql operation type: ${operation.operation}`
    );
  }

  const config: Record<string, unknown> = {
    kotlinPackage: `${KOTLIN_BASE_PACKAGE}.${CONNECTOR_NAME}`,
    operationName,
    operationType
  };

  if (configVariables.length > 0) {
    config.variables = configVariables;
  }

  if (flattenedConfigVariables && flattenedConfigVariables.size > 0) {
    config.flattenedVariables = Array.from(
      flattenedConfigVariables.entries()
    ).map(([variableName, typeInfo]) => {
      return {
        name: variableName,
        type: typeInfo.name,
        isList: typeInfo.isList,
        isNullable: typeInfo.isNullable
      };
    });
  }

  const configText = JSON.stringify(config, null, 2);
  console.log(configText);

  const tempy = await import('tempy');
  const jsonConfigFile = tempy.temporaryWriteSync(configText);

  try {
    const args = [
      goExecutable,
      'run',
      '-C',
      goAppDir,
      '.',
      '--',
      jsonConfigFile,
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
    fs.unlinkSync(jsonConfigFile);
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

function parseGraphQLOperations(): graphql.OperationDefinitionNode[] {
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

  return Array.from(operations.values());
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

export interface FieldInfo {
  name: string;
  type: string;
  isList: boolean;
  isNullable: boolean;
  fields: FieldInfo[];
}

main();
