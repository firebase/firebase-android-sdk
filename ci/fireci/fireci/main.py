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

from . import commands
from . import plugins
from .internal import main

logging.basicConfig(
    datefmt='%Y-%m-%d %H:%M:%S %z %Z',
    format='[%(levelname).1s] %(asctime)s %(name)s: %(message)s',
    level=logging.INFO,
)
logging.getLogger('fireci').setLevel(logging.DEBUG)

plugins.discover()

cli = main
