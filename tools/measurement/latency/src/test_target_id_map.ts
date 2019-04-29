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

import { Map } from 'immutable';

export const TEST_TARGET_ID_MAP: Map<string, number> = Map({
  'postsubmit-gob-sync': 1,
  'apksize-metrics-upload': 2,
  'coverage-metrics-upload': 3,
  'fireci': 4,
  'copyright-check': 5,
  'check-changed': 6,
  'connected-check-changed': 7,
  'build-plugins-check': 8,
  'smoke-tests-debug': 9,
  'smoke-tests-release': 10,
  'device-check-changed': 11,
});
