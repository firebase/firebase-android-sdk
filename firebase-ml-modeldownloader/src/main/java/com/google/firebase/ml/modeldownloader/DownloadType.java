// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader;

/**
 * @deprecated Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom
 *     models, you must migrate to another solution. You can use Cloud Storage for Firebase as an
 *     alternative for hosting custom models. For more info, see
 *     https://firebase.google.com/docs/ml/migrate-to-cloud-storage
 */
@Deprecated
public enum DownloadType {
  /**
   * Use local model when present, otherwise download and return latest model
   *
   * @deprecated Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom
   *     models, you must migrate to another solution. You can use Cloud Storage for Firebase as an
   *     alternative for hosting custom models. For more info, see
   *     https://firebase.google.com/docs/ml/migrate-to-cloud-storage
   */
  @Deprecated
  LOCAL_MODEL,
  /**
   * When local model present, use local model and download latest model in background. Otherwise,
   * download and return latest model.
   *
   * @deprecated Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom
   *     models, you must migrate to another solution. You can use Cloud Storage for Firebase as an
   *     alternative for hosting custom models. For more info, see
   *     https://firebase.google.com/docs/ml/migrate-to-cloud-storage
   */
  @Deprecated
  LOCAL_MODEL_UPDATE_IN_BACKGROUND,
  /**
   * Always return latest model, check for latest model and download new model (when needed) before
   * returning.
   *
   * @deprecated Firebase ML is deprecated and will be shut down on June 15, 2027. To host custom
   *     models, you must migrate to another solution. You can use Cloud Storage for Firebase as an
   *     alternative for hosting custom models. For more info, see
   *     https://firebase.google.com/docs/ml/migrate-to-cloud-storage
   */
  @Deprecated
  LATEST_MODEL
}
