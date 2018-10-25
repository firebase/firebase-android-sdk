#!/bin/bash
#
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

# First, you need to create a project, and bucket to use for the FTL runs.

#############################################################################################
## Note, running this will incur any FTL-related charges.                                  ##
## We do require manual setting of the script arguments to ensure you've read this notice. ##
#############################################################################################

PROJECT_ID={{add your project id here}}
BUCKET_NAME={{add your bucket name here}}

# Assemble the app
./gradlew :fiamui-app:assembleDebug :fiamui-app:assembleDebugAndroidTest

# Choose the project
gcloud config set project $PROJECT_ID

# Show the storage bucket
echo "Tests launching!"

# Run the tests on the following devices
#   * Pixel 2, API 27 - good for testing the ideal case
#   * OnePlus, API 22 - popular problematic phone model
#   * Nexus 7, API 19 - small tablet screen, outdated API
#   * Galaxy S7E, API 23 - popular high-end phone model, samsung exposure
#   * Low Res Phone, API 23 - capture very low-res cases
#   * Moto X, API 19 - popular old phone model, low API
gcloud firebase test android run \
  --type instrumentation \
  --results-bucket=$BUCKET_NAME \
  --app fiamui-app/build/outputs/apk/debug/fiamui-app-debug.apk  \
  --test fiamui-app/build/outputs/apk/androidTest/debug/fiamui-app-debug-androidTest.apk \
  --device model=Pixel2,version=27,locale=en,orientation=portrait  \
  --device model=Pixel2,version=27,locale=en,orientation=landscape \
  --device model=A0001,version=22,locale=en,orientation=portrait \
  --device model=A0001,version=22,locale=en,orientation=landscape \
  --device model=Nexus7,version=19,locale=en,orientation=portrait \
  --device model=Nexus7,version=19,locale=en,orientation=landscape \
  --device model=hero2lte,version=23,locale=en,orientation=portrait \
  --device model=hero2lte,version=23,locale=en,orientation=landscape \
  --device model=NexusLowRes,version=23,locale=en,orientation=portrait \
  --device model=NexusLowRes,version=23,locale=en,orientation=landscape \
  --device model=victara,version=19,locale=en,orientation=portrait \
  --device model=victara,version=19,locale=en,orientation=landscape
