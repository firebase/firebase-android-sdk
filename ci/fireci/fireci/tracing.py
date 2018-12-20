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

from opencensus.trace import execution_context
from opencensus.trace import tracer as tracer_module
from opencensus.trace.exporters import stackdriver_exporter
from opencensus.trace.exporters.transports import background_thread
from opencensus.trace.propagation import text_format


def initialize(stackdriver=False):
  exporter = None
  if stackdriver:
    exporter = stackdriver_exporter.StackdriverExporter(
        transport=background_thread.BackgroundThreadTransport)
  tracer = tracer_module.Tracer(exporter=exporter)
  tracer.store_tracer()


def tracer():
  return execution_context.get_opencensus_tracer()


_propagator = text_format.TextFormatPropagator()


def propagate_into(into_dict):
  return _propagator.to_carrier(tracer().span_context, into_dict)
