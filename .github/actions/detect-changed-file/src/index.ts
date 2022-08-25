/**
 * Copyright 2022 Google LLC
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

import * as core from '@actions/core';
import * as exec from '@actions/exec';
import * as glob from '@actions/glob';

import { relative } from 'path';

check().catch(core.setFailed);

async function check(): Promise<void> {
  const files = await listChangedFiles();
  const paths = await listGlobbedPaths();

  core.info(['', 'Globbed paths:', ...paths].join('\n'));

  const matched = paths
    .map((x) => relative('.', x))
    .filter((x) => files.includes(x));

  core.info(['', 'Matched paths:', ...matched].join('\n'));

  core.setOutput('changed', matched.length !== 0);
}

async function listChangedFiles(): Promise<string[]> {
  const command = 'git diff --name-only --submodule=diff HEAD^1 HEAD';
  const process = await exec.getExecOutput(command);
  return process.stdout.trim().split('\n');
}

async function listGlobbedPaths(): Promise<string[]> {
  const path = core.getInput('path', { required: true });
  const globber = await glob.create(path);
  return globber.glob();
}
