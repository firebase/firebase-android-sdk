/**
 * @license
 * Copyright 2019 Google Inc.
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

import * as k8s from '@kubernetes/client-node';
import * as fs from 'fs';
import * as mkdirp from 'mkdirp';
import * as path from 'path';
import { argv } from 'yargs';

import { TEST_TARGET_ID_MAP } from './test_target_id_map';
import { ProwJob, ReplaceMeasurement, Report, Table } from './types';

async function getProwJobs(api: k8s.Custom_objectsApi): Promise<ProwJob[]> {
  const group = 'prow.k8s.io';
  const version = 'v1';
  const namespace = 'default';
  const plural = 'prowjobs';

  return new Promise((resolve, reject) => {
    api
      .listNamespacedCustomObject(group, version, namespace, plural)
      .then(res => {
        if (res.response.statusCode === 200) {
          resolve(res.body.items as ProwJob[]);
        } else {
          const error = {
            statusCode: res.response.statusCode,
            statusMessage: res.response.statusMessage,
          };
          reject(`Query k8s cluster failed with error "${error}".`);
        }
      })
      .catch(error => {
        reject(`Query k8s cluter failed with error "${error}".`);
      });
  });
}

function isAndroidSdkJob(prowJob: ProwJob): boolean {
  return TEST_TARGET_ID_MAP.has(prowJob.spec.job);
}

function constructReport(prowJobs: ProwJob[]): Report {
  /* Temporarily allow snake case naming for report object assembly. */
  /* tslint:disable:variable-name */
  const table_name = 'TestResults';
  const column_names = [
    'test_id',
    'test_target_id',
    'test_start_time',
    'test_end_time',
    'test_state',
    'test_type',
    'pull_request_id',
  ];
  const replace_measurements = prowJobs
    .filter(isAndroidSdkJob)
    .map(ReplaceMeasurement.fromProwJob);

  return new Report([
    new Table(table_name, column_names, replace_measurements),
  ]);
  /* tslint:enable:variable-name */
}

async function generateReportFile(report: Report): Promise<void> {
  const data = report.toJsonString();
  if (argv.reportFile) {
    return new Promise((resolve, reject) => {
      const dir = path.dirname(argv.reportFile);
      mkdirp(dir, err => {
        if (err) {
          reject(`Failed to create directory at "${dir}", error "${err}".`);
        } else {
          const filename = path.basename(argv.reportFile);
          fs.writeFile(argv.reportFile, data, err => {
            if (err) {
              reject(`Failed to write to file "${filename}", error "${err}".`);
            } else {
              resolve();
            }
          });
        }
      });
    });
  } else {
    console.log(data);
  }
}

async function run(): Promise<void> {
  const kc = new k8s.KubeConfig();
  kc.loadFromDefault();
  const api = kc.makeApiClient(k8s.Custom_objectsApi);

  const prowJobs = await getProwJobs(api);
  const report = constructReport(prowJobs);
  await generateReportFile(report);
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
