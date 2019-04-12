// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins.measurement.utils.enums

import com.google.gson.annotations.SerializedName

enum ColumnName {
    @SerializedName('pull_request_id')
    PULL_REQUEST_ID,

    @SerializedName('sdk_id')
    SDK_ID,

    @SerializedName('apk_size')
    APK_SIZE,

    @SerializedName('coverage_percent')
    COVERAGE_PERCENT,

    @SerializedName('project')
    PROJECT,

    @SerializedName('subproject')
    SUBPROJECT,

    @SerializedName('build type')
    BUILD_TYPE

    @Override
    String toString() {
        return ColumnName.getField(this.name())
                .getAnnotation(SerializedName.class).value()
    }

}
