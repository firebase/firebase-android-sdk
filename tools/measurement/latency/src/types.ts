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

import * as moment from 'moment';

import { TEST_TARGET_ID_MAP } from './test_target_id_map';

export interface ProwJob {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
  };
  spec: {
    job: string;
    refs: {
      pulls?: Array<{ number: number }>;
    };
    type: string;
  };
  status: {
    startTime: string;
    completionTime: string;
    state: string;
  };
}

export class ReplaceMeasurement extends Array<string | number> {
  private static readonly DATETIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

  static fromProwJob(job: ProwJob): ReplaceMeasurement {
    const testId = job.metadata.name;
    const testTargetId = TEST_TARGET_ID_MAP.get(job.spec.job);
    const startTime = moment(job.status.startTime);
    const endTime = moment(job.status.completionTime);
    const testStartTime = startTime.format(ReplaceMeasurement.DATETIME_FORMAT);
    const testEndTime = endTime.format(ReplaceMeasurement.DATETIME_FORMAT);
    const testState = job.status.state;
    const testType = job.spec.type;
    const pulls = job.spec.refs.pulls;
    const pullRequestId = pulls ? pulls[0].number : -1;

    return [
      testId,
      testTargetId,
      testStartTime,
      testEndTime,
      testState,
      testType,
      pullRequestId,
    ];
  }
}

/* Fields in the Table class need to be in snake case. */
/* tslint:disable:variable-name */
export class Table {
  table_name: string;
  column_names: string[];
  replace_measurements: ReplaceMeasurement[];

  constructor(
    table_name: string,
    column_names: string[],
    replace_measurements: ReplaceMeasurement[]
  ) {
    this.table_name = table_name;
    this.column_names = column_names;
    this.replace_measurements = replace_measurements;
  }
}
/* tslint:enable:variable-name */

export class Report {
  tables: Table[];

  constructor(tables: Table[]) {
    this.tables = tables;
  }

  toJsonString(): string {
    return JSON.stringify(this);
  }
}
