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

package com.google.firebase.gradle.plugins.measurement.utils.reports

import com.google.firebase.gradle.plugins.measurement.utils.enums.ColumnName
import com.google.firebase.gradle.plugins.measurement.utils.enums.TableName
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * A Java bean for constructing a {@link JsonReport} or a {@link TableReport}.
 */
class Table {
    @SerializedName("table_name")
    TableName tableName

    @SerializedName("column_names")
    List<ColumnName> columnNames

    @SerializedName("replace_measurements")
    List<List<Object>> replaceMeasurements

    @Override
    String toString() {
        return new Gson().toJson(this)
    }

}
