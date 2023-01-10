# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import logging
import re
import tempfile

from click import ClickException
from google.cloud import storage
from pathlib import Path
from typing import List, TypedDict


logger = logging.getLogger('fireci.macrobenchmark')
DataPoint = TypedDict('DataPoint', {'duration': float, 'device': str, 'trace': str, 'run_id': str})


def collect_data_points(ftl_results_dir: List[str], local_reports_dir: Path) -> List[DataPoint]:
  if not ftl_results_dir and not local_reports_dir:
    raise ClickException('Neither ftl-results-dir or local-reports-dir is provided.')
  elif ftl_results_dir and not local_reports_dir:
    temp_dir = _download(ftl_results_dir)
    return _extract_raw_data(temp_dir)
  elif not ftl_results_dir and local_reports_dir:
    return _extract_raw_data(local_reports_dir)
  else:
    raise ClickException('Should specify either ftl-results-dir or local-reports-dir, not both.')


def _download(ftl_results_dirs: List[str]) -> Path:
  ftl_results_bucket = 'fireescape-benchmark-results'
  gcs = storage.Client()

  temp_dir = tempfile.mkdtemp(prefix='ftl-results-')
  for ftl_results_dir in ftl_results_dirs:
    blobs = gcs.list_blobs(ftl_results_bucket, prefix=ftl_results_dir)
    files = [f for f in blobs if f.name.endswith('.json')]
    for file in files:
      device = re.search(r'([^/]*)/artifacts/', file.name).group(1)
      report_dir = Path(temp_dir).joinpath(ftl_results_dir, device)
      report_dir.mkdir(parents=True, exist_ok=True)
      filename = file.name.split('/')[-1]
      file.download_to_filename(report_dir.joinpath(filename))
      logger.info(f'Downloaded "{file.name}" to "{report_dir}"')

  return Path(temp_dir)


def _extract_raw_data(test_reports_dir: Path) -> List[DataPoint]:
  data_points: List[DataPoint] = []
  reports = sorted(list(test_reports_dir.rglob("*-benchmarkData.json")))
  for report in reports:
    logger.info(f'Processing "{report}" ...')

    run_id = str(report.relative_to(test_reports_dir)).split('/')[0]
    with open(report) as file:
      obj = json.load(file)
      build_context = obj['context']['build']
      device = f'{build_context["device"]}-{build_context["version"]["sdk"]}'
      for metric in obj['benchmarks'][0]['metrics'].keys():
        measurements = obj['benchmarks'][0]['metrics'][metric]['runs']
        trace = metric[:-2]  # TODO(yifany): .removesuffix('Ms') w/ python 3.9+
        data_points.extend([{
          'duration': measurement,
          'device': device,
          'trace': trace,
          'run_id': run_id
        } for measurement in measurements])
  logger.info(f'Extracted {len(data_points)} data points from reports in "{test_reports_dir}"')
  return data_points
