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
import * as moment from 'moment';
import * as path from 'path';
import { argv } from 'yargs';

import { TEST_TARGET_ID_MAP } from './test_target_id_map';
import { ProwJob, ReplaceMeasurement, Report } from './types';

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

function convertProwJobToReplaceMeasurement(job: ProwJob): ReplaceMeasurement {
  // test_id
  const testId = job.metadata.name;
  // test_target_id
  const testTargetId = TEST_TARGET_ID_MAP.get(job.spec.job);
  // test_start_time, test_end_time, test_duration
  const startTime = moment(job.status.startTime);
  const endTime = moment(job.status.completionTime);
  const format = 'YYYY-MM-DD HH:mm:ss';
  const testStartTime = startTime.format(format);
  const testEndTime = endTime.format(format);
  const testDuration = endTime.diff(startTime, 'seconds');
  // test_state
  const testState = job.status.state;
  // test_type
  const testType = job.spec.type;
  // pull_request_id
  const pulls = job.spec.refs.pulls;
  const pullRequestId = pulls ? pulls[0].number : -1;

  return [
    testId,
    testTargetId,
    testStartTime,
    testEndTime,
    testDuration,
    testState,
    testType,
    pullRequestId,
  ];
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
    'test_duration',
    'test_state',
    'test_type',
    'pull_request_id',
  ];
  const replace_measurements = prowJobs
    .filter(isAndroidSdkJob)
    .map(convertProwJobToReplaceMeasurement);

  return { tables: [{ table_name, column_names, replace_measurements }] };
  /* tslint:enable:variable-name */
}

async function generateReportFile(report: Report): Promise<void> {
  const data = JSON.stringify(report);
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
