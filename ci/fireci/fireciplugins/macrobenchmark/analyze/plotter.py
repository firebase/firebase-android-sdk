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
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns

from pathlib import Path


logger = logging.getLogger('fireci.macrobenchmark')
sns.set()


def plot_graph(trace: str, device: str, data: pd.DataFrame, output_dir: Path):
  logger.info(f'Plotting graphs for trace "{trace}" on device "{device}" ...')

  output_dir.mkdir(parents=True, exist_ok=True)

  unique_run_ids = len(data['run_id'].unique())
  col_wrap = int(np.ceil(np.sqrt(unique_run_ids)))

  histograms = sns.displot(data=data, x='duration', kde=True, col="run_id", col_wrap=col_wrap)
  histograms.set_axis_labels(x_var=f'{trace} (ms)')
  histograms.set_titles(f'{device} ({{col_var}} = {{col_name}})')
  histograms.savefig(output_dir.joinpath('histograms.svg'))
  plt.close(histograms.fig)

  distributions = sns.displot(
    data=data, x='duration', kde=True, height=8,
    hue='run_id', palette='muted', multiple='dodge'
  )
  distributions.set_axis_labels(x_var=f'{trace} (ms)').set(title=device)
  distributions.savefig(output_dir.joinpath('distributions.svg'))
  plt.close(distributions.fig)

  logger.info(f'Graphs saved in: {output_dir}')


def plot_diff_graph(
    trace: str,
    device: str,
    control: pd.DataFrame,
    experimental: pd.DataFrame,
    output_dir: Path
):
  logger.info(f'Plotting distribution diff graph for trace "{trace}" on device "{device}" ...')

  output_dir.mkdir(parents=True, exist_ok=True)

  control_run_ids = control['run_id']
  experimental_run_ids = experimental['run_id']
  all_data = pd.concat([control, experimental])

  palette = {**{x: 'b' for x in control_run_ids}, **{x: 'r' for x in experimental_run_ids}}

  distribution_diff = sns.displot(
    data=all_data, x='duration', kde=True, height=8,
    hue='run_id', palette=palette, multiple='dodge'
  )
  distribution_diff.set_axis_labels(x_var=f'{trace} (ms)').set(title=device)
  distribution_diff.savefig(output_dir.joinpath('distribution_diff.svg'))
  plt.close(distribution_diff.fig)

  logger.info(f'Graph saved in: {output_dir}')
