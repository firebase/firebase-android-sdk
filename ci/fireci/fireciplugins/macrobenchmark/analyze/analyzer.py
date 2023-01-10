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

import logging
import tempfile
import pandas as pd

from .aggregator import calculate_statistic, calculate_statistic_diff
from .plotter import plot_graph, plot_diff_graph
from .utils import collect_data_points, DataPoint
from click import progressbar
from pathlib import Path
from typing import List


logger = logging.getLogger('fireci.macrobenchmark')


def start(
    diff_mode: bool,
    ftl_results_dir: List[str],
    local_reports_dir: Path,
    ctl_ftl_results_dir: List[str],
    ctl_local_reports_dir: Path,
    exp_ftl_results_dir: List[str],
    exp_local_reports_dir: Path,
    output_dir: Path
):
  logger.info('Starting to analyze macrobenchmark test results ...')

  if not output_dir:
    output_dir = Path(tempfile.mkdtemp(prefix='macrobenchmark-analysis-'))
    logger.info(f'Created temporary dir "{output_dir}" to save analysis results')

  if not diff_mode:
    data_points = collect_data_points(ftl_results_dir, local_reports_dir)
    _process(data_points, output_dir)
  else:
    logger.info('Running in diff mode ...')
    ctl_data_points = collect_data_points(ctl_ftl_results_dir, ctl_local_reports_dir)
    exp_data_points = collect_data_points(exp_ftl_results_dir, exp_local_reports_dir)
    _diff(ctl_data_points, exp_data_points, output_dir)

  logger.info(f'Completed analysis and saved output in: {output_dir}')


def _process(data_points: List[DataPoint], output_dir: Path) -> None:
  data = pd.DataFrame(data_points)
  traces = sorted(data['trace'].unique())
  devices = sorted(data['device'].unique())

  trace_device_combinations = [(trace, device) for trace in traces for device in devices]

  with progressbar(trace_device_combinations) as combinations:
    for trace, device in combinations:
      combination_dir = output_dir.joinpath(trace, device)
      subset = _filter_subset(data, trace, device)
      calculate_statistic(trace, device, subset, combination_dir)
      plot_graph(trace, device, subset, combination_dir)


def _diff(
    ctl_data_points: List[DataPoint],
    exp_data_points: List[DataPoint],
    output_dir: Path
) -> None:
  ctl_data = pd.DataFrame(ctl_data_points)
  exp_data = pd.DataFrame(exp_data_points)
  all_data = pd.concat([ctl_data, exp_data])

  traces = sorted(all_data['trace'].unique())
  devices = sorted(all_data['device'].unique())

  trace_device_combinations = [(trace, device) for trace in traces for device in devices]

  with progressbar(trace_device_combinations) as combinations:
    for trace, device in combinations:
      combination_dir = output_dir.joinpath(trace, device)

      ctl_subset = _filter_subset(ctl_data, trace, device)
      exp_subset = _filter_subset(exp_data, trace, device)

      calculate_statistic_diff(trace, device, ctl_subset, exp_subset, combination_dir)
      plot_diff_graph(trace, device, ctl_subset, exp_subset, combination_dir)


def _filter_subset(data: pd.DataFrame, trace: str, device: str) -> pd.DataFrame:
  return data.loc[
    (data['trace'] == trace) & (data['device'] == device),
    ['duration', 'run_id']
  ]
