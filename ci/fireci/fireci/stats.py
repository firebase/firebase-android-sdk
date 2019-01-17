# Copyright 2018 Google LLC
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
import google.auth
import google.auth.exceptions
from opencensus.stats import stats
from opencensus.stats import measure
from opencensus.stats import view
from opencensus.stats import aggregation
from opencensus.tags import tag_key
from opencensus.tags import tag_map
from opencensus.stats.exporters.base import StatsExporter
from opencensus.stats.exporters import stackdriver_exporter

_logger = logging.getLogger('fireci.emulator')
STATS = stats.Stats()

m_latency_ms = measure.MeasureFloat("latency", "The latency in milliseconds",
                                    "ms")

key_method = tag_key.TagKey("method")


class Exporter(StatsExporter):

  def on_register_view(self, view):
    _logger.info("on_register_view %s", view)

  def emit(self, view_datas):
    _logger.info("emit %s", self.repr_data(view_datas))

  def export(self, view_data):
    _logger.info("export %s", self.repr_data(view_data))

  @staticmethod
  def repr_data(view_data):
    return [
        "ViewData<view={}, start={}, end={}>".format(d.view, d.start_time,
                                                     d.end_time)
        for d in view_data
    ]


def new_exporter():
  try:
    _, project_id = google.auth.default()
    return stackdriver_exporter.new_stats_exporter(
        stackdriver_exporter.Options(project_id))
  except google.auth.exceptions.DefaultCredentialsError:
    return Exporter()


def configure():
  latency_view = view.View(
      "myapp/latency", "The distribution of the latencies", [key_method],
      m_latency_ms,
      aggregation.DistributionAggregation([25, 100, 200, 400, 800, 10000]))
  STATS.view_manager.register_view(latency_view)
  STATS.view_manager.register_exporter(new_exporter())


def measure(value):
  mmap = STATS.stats_recorder.new_measurement_map()
  mmap.measure_float_put(m_latency_ms, value)
  mmap.record()
