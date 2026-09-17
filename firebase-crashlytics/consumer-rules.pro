# Copyright 2026 Google LLC
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

# ProfilingManager was introduced in Android 15 (API level 35) and ProfilingTrigger
# in Android 16 (API level 36). These APIs are guarded at runtime with SDK version
# checks. Consumer applications compiling against older Android SDK versions
# (for example, API level 34 or 35) do not have these classes in their compile
# classpath android.jar. Suppress R8 missing class warnings
-dontwarn android.os.ProfilingManager
-dontwarn android.os.ProfilingTrigger**
