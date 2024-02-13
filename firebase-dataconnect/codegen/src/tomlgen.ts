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

export interface VariableInfo {
  name: string;
  type: string;
  isList: boolean;
  isNullable: boolean;
}

export function* tomlConfigLines(config: {
  kotlinPackage: string;
  operationName: string;
  variables: VariableInfo[];
}): Generator<string> {
  yield `kotlinPackage = '${config.kotlinPackage}'`;
  yield `operationName = '${config.operationName}'`;

  for (const variableInfo of config.variables) {
    yield `[[variables]]`;
    yield `name = '${variableInfo.name}'`;
    yield `type = '${variableInfo.type}'`;
    yield `isList = ${variableInfo.isList ? 'true' : 'false'}`;
    yield `isNullable = ${variableInfo.isNullable ? 'true' : 'false'}`;
  }
}
