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
import functools
import google.auth
import google.auth.exceptions
import logging
import os
import time

from opencensus import tags
from opencensus.stats import aggregation
from opencensus.stats import measure
from opencensus.stats import stats
from opencensus.stats import view
from opencensus.stats.exporters import stackdriver_exporter
from opencensus.stats.exporters.base import StatsExporter
from opencensus.tags import execution_context
from opencensus.tags.propagation import binary_serializer

_logger = logging.getLogger('fireci.stats')
STATS = stats.Stats()

_m_latency = measure.MeasureFloat("latency", "The latency in milliseconds",
                                  "ms")
_m_success = measure.MeasureInt("success", "Indicated success or failure.", "1")

_key_stage = tags.TagKey("stage")

_TAGS = [
    _key_stage,
    tags.TagKey("repo_owner"),
    tags.TagKey("repo_name"),
    tags.TagKey("pull_number"),
    tags.TagKey("job_name"),
    tags.TagKey("build_id"),
    tags.TagKey("job_type"),
]

_METRICS_ENABLED = False


class StdoutExporter(StatsExporter):
  """Fallback exporter in case stackdriver cannot be configured."""

  def on_register_view(self, view):
    pass

  def emit(self, view_datas):
    _logger.info("emit %s", self.repr_data(view_datas))

  def export(self, view_data):
    _logger.info("export %s", self._repr_data(view_data))

  @staticmethod
  def _repr_data(view_data):
    return [
        "ViewData<view={}, start={}, end={}>".format(d.view, d.start_time,
                                                     d.end_time)
        for d in view_data
    ]


def _new_exporter():
  """
     Initializes a metrics exporter.

     Tries to initialize a Stackdriver exporter, falls back to StdoutExporter.
  """
  try:
    _, project_id = google.auth.default()
    return stackdriver_exporter.new_stats_exporter(
        stackdriver_exporter.Options(project_id=project_id, resource='global'))
  except google.auth.exceptions.DefaultCredentialsError:
    _logger.exception("Using stdout exporter")
    return StdoutExporter()


def configure():
  """Globally enables metrics collection."""
  global _METRICS_ENABLED
  if _METRICS_ENABLED:
    return
  _METRICS_ENABLED = True

  STATS.view_manager.register_exporter(_new_exporter())
  latency_view = view.View(
      "fireci/latency", "Latency of fireci execution stages", _TAGS, _m_latency,
      aggregation.LastValueAggregation())
  success_view = view.View(
      "fireci/success", "Success indication of fireci execution stages", _TAGS,
      _m_success, aggregation.LastValueAggregation())
  STATS.view_manager.register_view(latency_view)
  STATS.view_manager.register_view(success_view)

  context = tags.TagMap()
  for tag in _TAGS:
    if tag.upper() in os.environ:
      context.insert(tag, tags.TagValue(os.environ[tag.upper()]))

  execution_context.set_current_tag_map(context)


@contextlib.contextmanager
def _measure(name):
  tmap = copy.deepcopy(execution_context.get_current_tag_map())
  tmap.insert(_key_stage, name)
  start = time.time()
  try:
    yield
  except:
    mmap = STATS.stats_recorder.new_measurement_map()
    mmap.measure_int_put(_m_success, 0)
    mmap.record(tmap)
    raise

  elapsed = (time.time() - start) * 1000
  mmap = STATS.stats_recorder.new_measurement_map()
  mmap.measure_float_put(_m_latency, elapsed)
  mmap.measure_int_put(_m_success, 1)
  mmap.record(tmap)
  _logger.info("%s took %sms", name, elapsed)


@contextlib.contextmanager
def measure(name):
  """Context manager that measures the time it took for a block of code to execute."""
  if not _METRICS_ENABLED:
    yield
    return
  with _measure(name):
    yield


def measure_call(name):
  """Function decorator that measures the time it took to execute the target function."""

  def decorator(f):

    def decorated(*args, **kwargs):
      with measure(name):
        return f(*args, **kwargs)

    functools.update_wrapper(decorated, f)
    return decorated

  return decorator


def propagate_context_into(data_dict):
  """Propagates Tag context into a dictionary."""
  if not _METRICS_ENABLED:
    return
  value = binary_serializer.BinarySerializer().to_byte_array(
      execution_context.get_current_tag_map())
  data_dict['OPENCENSUS_STATS_CONTEXT'] = base64.b64encode(value)
