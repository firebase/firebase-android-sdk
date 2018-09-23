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

import importlib
import pkgutil
import fireciplugins


def discover():
  """Discovers fireci plugins available on PYTHONPATH under firebaseplugins subpackages.

     Discovery works by importing all direct subpackages of firebaseplugins and importing them,
     plugins are supposed to register ci_command's with fireci in their __init__.py files directly
     or by importing from their own subpackages.

     Note: plugins *must* define the `firebaseplugins` package as a namespace package.
           See: https://packaging.python.org/guides/packaging-namespace-packages/
  """
  modules = pkgutil.iter_modules(fireciplugins.__path__,
                                 fireciplugins.__name__ + ".")
  for _, name, _ in modules:
    importlib.import_module(name)
