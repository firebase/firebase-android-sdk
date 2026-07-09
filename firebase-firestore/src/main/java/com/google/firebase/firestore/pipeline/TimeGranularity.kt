// Copyright 2026 Google LLC
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

package com.google.firebase.firestore.pipeline

/** Specify time granularity for range window frames or date/time expressions. */
enum class TimeGranularity(val canonicalString: String) {
  MILLISECOND("millisecond"),
  SECOND("second"),
  MINUTE("minute"),
  HOUR("hour"),
  DAY("day"),
  WEEK("week"),
  WEEK_MONDAY("week(monday)"),
  WEEK_TUESDAY("week(tuesday)"),
  WEEK_WEDNESDAY("week(wednesday)"),
  WEEK_THURSDAY("week(thursday)"),
  WEEK_FRIDAY("week(friday)"),
  WEEK_SATURDAY("week(saturday)"),
  WEEK_SUNDAY("week(sunday)"),
  ISOWEEK("isoweek"),
  MONTH("month"),
  QUARTER("quarter"),
  YEAR("year")
}
