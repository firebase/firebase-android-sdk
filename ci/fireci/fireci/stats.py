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

import base64
import contextlib
import copy
import google.auth
import google.auth.exceptions
import logging
import os
import time

from opencensus.stats import stats
from opencensus.stats import measure
from opencensus.stats import view
from opencensus.stats import aggregation
from opencensus import tags
from opencensus.tags import execution_context
from opencensus.tags.propagation import binary_serializer
from opencensus.stats.exporters.base import StatsExporter
from opencensus.stats.exporters import stackdriver_exporter

_logger = logging.getLogger('fireci.emulator')
STATS = stats.Stats()

m_latency_ms = measure.MeasureFloat("latency", "The latency in milliseconds",
                                    "ms")
m_success = measure.MeasureInt("success", "Indicated success or failure.", "1")

key_stage = tags.TagKey("stage")
key_repo_owner = tags.TagKey("repo_owner")
key_repo_name = tags.TagKey("repo_name")
key_pull_number = tags.TagKey("pull_number")
key_job_name = tags.TagKey("job_name")

TAGS = [
    key_stage,
    key_repo_owner,
    key_repo_name,
    key_pull_number,
    key_job_name,
]


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
    _logger.error("Using stdout exporter")
    return Exporter()


def configure():
  STATS.view_manager.register_exporter(new_exporter())
  latency_view = view.View("fireci/latency",
                           "Latency of fireci execution stages", TAGS,
                           m_latency_ms, aggregation.LastValueAggregation())
  success_view = view.View("fireci/success",
                           "Success indication of fireci execution stages",
                           TAGS, m_success, aggregation.LastValueAggregation())
  STATS.view_manager.register_view(latency_view)
  STATS.view_manager.register_view(success_view)

  context = tags.TagMap()
  for tag in TAGS:
    if tag.upper() in os.environ:
      context.insert(tag, tags.TagValue(os.environ[tag.upper()]))

  execution_context.set_current_tag_map(context)


@contextlib.contextmanager
def measure(name):
  tmap = copy.deepcopy(execution_context.get_current_tag_map())
  tmap.insert(key_stage, name)
  start = time.time()
  try:
    yield
  except:
    mmap = STATS.stats_recorder.new_measurement_map()
    mmap.measure_int_put(m_success, 0)
    mmap.record(tmap)
    raise

  elapsed = (time.time() - start) * 1000
  mmap = STATS.stats_recorder.new_measurement_map()
  mmap.measure_float_put(m_latency_ms, elapsed)
  mmap.measure_int_put(m_success, 1)
  mmap.record(tmap)
  _logger.info("%s took %sms", name, elapsed)


def measure_call(name):

  def decorator(f):

    def decorated(*args, **kwargs):
      with measure(name):
        f(*args, **kwargs)

    return decorated

  return decorator


def propagate_context_into(data_dict):
  value = binary_serializer.BinarySerializer().to_byte_array(
      execution_context.get_current_tag_map())
  data_dict['OPENCENSUS_STATS_CONTEXT'] = base64.b64encode(value)
