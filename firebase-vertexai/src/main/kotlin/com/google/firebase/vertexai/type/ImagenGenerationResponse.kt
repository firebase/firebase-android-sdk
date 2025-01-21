/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.vertexai.type

/**
 * Represents a response from Imagen call to [ImageModel#generateImages]
 *
 * @param images contains the generated images
 * @param filteredReason if fewer images were generated than were requested, this field will contain
 * the reason they were filtered out.
 */
public class ImagenGenerationResponse<T>(
  public val images: List<T>,
  public val filteredReason: String?,
) {}
