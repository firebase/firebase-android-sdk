/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ai.type

import kotlinx.serialization.Serializable

/**
 * An object that represents a latitude/longitude pair.
 *
 * @param latitude The latitude in degrees. It must be in the range [-90.0, +90.0].
 * @param longitude The longitude in degrees. It must be in the range [-180.0, +180.0].
 */
@Serializable public data class LatLng(val latitude: Double, val longitude: Double)
