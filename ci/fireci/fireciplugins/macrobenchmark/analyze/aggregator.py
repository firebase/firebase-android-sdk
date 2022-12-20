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
import pandas as pd
import seaborn as sns

from pathlib import Path

logger = logging.getLogger('fireci.macrobenchmark')
sns.set()


def calculate_statistic(trace: str, device: str, data: pd.DataFrame, output_dir: Path = None):
  logger.info(f'Calculating statistics for trace "{trace}" on device "{device}" ...')

  # Calculate percentiles per each run_id
  quantiles = [0.1, 0.25, 0.5, 0.75, 0.9]
  percentiles = data.groupby('run_id').quantile(quantiles, numeric_only=True)
  percentiles.index.set_names('percentile', level=1, inplace=True)
  percentiles = percentiles.reset_index(['run_id', 'percentile'])
  percentiles = percentiles.pivot(index='run_id', columns='percentile', values='duration')

  def mapper(quantile: float) -> str: return f'p{int(quantile * 100)}'

  percentiles.rename(mapper=mapper, axis='columns', inplace=True)

  # Calculate dispersions of each percentile over all runs
  mean = percentiles.mean()
  std = percentiles.std()  # standard deviation
  cv = std / mean  # coefficient of variation (relative standard deviation)
  mad = (percentiles - percentiles.mean()).abs().mean()  # mean absolute deviation
  rmad = mad / mean  # relative mean absolute deviation (mad / mean)
  dispersions = pd.DataFrame([pd.Series(cv, name='cv'), pd.Series(rmad, name='rmad')])

  # Optionally save percentiles and dispersions to file
  if output_dir:
    output_dir.mkdir(parents=True, exist_ok=True)
    percentiles.to_json(output_dir.joinpath('percentiles.json'), orient='index')
    dispersions.to_json(output_dir.joinpath('dispersions.json'), orient='index')
    logger.info(f'Percentiles and dispersions saved in: {output_dir}')

  return percentiles, dispersions


def calculate_statistic_diff(
    trace: str,
    device: str,
    control: pd.DataFrame,
    experimental: pd.DataFrame,
    output_dir: Path = None,
):
  logger.info(f'Calculating statistic diff for trace "{trace}" on device "{device}" ...')

  ctl_percentiles, _ = calculate_statistic(trace, device, control, output_dir.joinpath("ctl"))
  exp_percentiles, _ = calculate_statistic(trace, device, experimental, output_dir.joinpath("exp"))

  ctl_mean = ctl_percentiles.mean()
  exp_mean = exp_percentiles.mean()

  delta = exp_mean - ctl_mean
  percentage = delta / ctl_mean

  # Optionally save statistics to file
  if output_dir:
    output_dir.mkdir(parents=True, exist_ok=True)
    delta.to_json(output_dir.joinpath('delta.json'))
    percentage.to_json(output_dir.joinpath('percentage.json'))
    logger.info(f'Percentiles diff saved in: {output_dir}')
