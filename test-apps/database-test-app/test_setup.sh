#Copyright 2018 Google LLC
#
#Licensed under the Apache License, Version 2.0 (the "License");
#you may not use this file except in compliance with the License.
#You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software
#distributed under the License is distributed on an "AS IS" BASIS,
#WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#See the License for the specific language governing permissions and
#limitations under the License.

#!/bin/bash
set -o nounset
set -e

#delete the restaurants collection
echo "Deleting the restaurants collection under project"
firebase database:remove "/restaurants" -y --project="$PROJECT_ID"

#create a test account test@mailinator.com
echo "Creating test accounts"
firebase auth:import accounts.json --hash-algo=SHA256 --rounds=1 --project="$PROJECT_ID"
