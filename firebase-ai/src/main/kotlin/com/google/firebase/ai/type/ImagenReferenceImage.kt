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
package com.google.firebase.ai.type

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.firebase.ai.common.GenerateImageRequest
import kotlinx.serialization.Serializable

/** Represents an reference image for an Imagen editing request */
@PublicPreviewAPI
public abstract class ImagenReferenceImage
internal constructor(
  internal val maskConfig: ImagenMaskConfig? = null,
  internal val subjectConfig: ImagenSubjectConfig? = null,
  internal val styleConfig: ImagenStyleConfig? = null,
  internal val controlConfig: ImagenControlConfig? = null,
  public val image: ImagenInlineImage? = null,
  public val referenceId: Int? = null,
) {

  internal fun toInternal(optionalReferenceId: Int): Internal {
    val referenceType =
      when (this) {
        is ImagenRawImage -> GenerateImageRequest.ReferenceType.RAW
        is ImagenMaskReference -> GenerateImageRequest.ReferenceType.MASK
        is ImagenSubjectReference -> GenerateImageRequest.ReferenceType.SUBJECT
        is ImagenStyleReference -> GenerateImageRequest.ReferenceType.STYLE
        is ImagenControlReference -> GenerateImageRequest.ReferenceType.CONTROL
        else -> {
          throw IllegalStateException(
            "${this.javaClass.simpleName} is not a known subtype of ImagenReferenceImage"
          )
        }
      }
    return Internal(
      referenceType = referenceType,
      referenceImage = image?.toInternal(),
      referenceId = referenceId ?: optionalReferenceId,
      subjectImageConfig = subjectConfig?.toInternal(),
      maskImageConfig = maskConfig?.toInternal(),
      styleImageConfig = styleConfig?.toInternal(),
      controlConfig = controlConfig?.toInternal(),
    )
  }

  @Serializable
  internal data class Internal(
    val referenceType: GenerateImageRequest.ReferenceType,
    val referenceImage: ImagenInlineImage.Internal?,
    val referenceId: Int,
    val subjectImageConfig: ImagenSubjectConfig.Internal?,
    val maskImageConfig: ImagenMaskConfig.Internal?,
    val styleImageConfig: ImagenStyleConfig.Internal?,
    val controlConfig: ImagenControlConfig.Internal?
  )
}

/**
 * Represents a reference image (provided or generated) to bound the created image via controlled
 * generation.
 * @param image the image provided, required if [enableComputation] is false
 * @param type the type of control reference image
 * @param referenceId the reference ID for this image, to be referenced in the prompt
 * @param enableComputation requests that the reference image be generated serverside instead of
 * provided
 * @param superpixelRegionSize if type is [ImagenControlType.COLOR_SUPERPIXEL] and
 * [enableComputation] is true, this will control the size of each superpixel region in pixels for
 * the generated referenced image
 * @param superpixelRuler if type is [ImagenControlType.COLOR_SUPERPIXEL] and [enableComputation] is
 * true, this will control the superpixel smoothness factor for the generated referenced image
 */
@PublicPreviewAPI
public class ImagenControlReference(
  type: ImagenControlType,
  image: ImagenInlineImage? = null,
  referenceId: Int? = null,
  enableComputation: Boolean? = null,
  superpixelRegionSize: Int? = null,
  superpixelRuler: Int? = null,
) :
  ImagenReferenceImage(
    controlConfig =
      ImagenControlConfig(type, enableComputation, superpixelRegionSize, superpixelRuler),
    image = image,
    referenceId = referenceId,
  ) {}

/**
 * Represents a mask for Imagen editing. This image (generated or provided) should contain only
 * black and white pixels, with black representing parts of the image which should not change.
 */
@PublicPreviewAPI
public abstract class ImagenMaskReference
internal constructor(maskConfig: ImagenMaskConfig, image: ImagenInlineImage? = null) :
  ImagenReferenceImage(maskConfig = maskConfig, image = image) {

  public companion object {
    /**
     * Generates two reference images of [ImagenRawImage] and [ImagenRawMask]. These images are
     * generated in this order:
     * * One [ImagenRawImage] containing the original image, padded out to the new dimensions with
     * black pixels, with the original image placed at the given placement
     * * One [ImagenRawMask] of the same dimensions containing white everywhere except at the
     * placement original image. This is the format expected by Imagen for outpainting requests.
     *
     * @param image the original image
     * @param newDimensions the new dimensions for outpainting. These new dimensions *must* be more
     * than the original image.
     * @param newPosition the placement of the original image within the new outpainted image.
     */
    @JvmOverloads
    @JvmStatic
    public fun generateMaskAndPadForOutpainting(
      image: ImagenInlineImage,
      newDimensions: Dimensions,
      newPosition: ImagenImagePlacement = ImagenImagePlacement.CENTER,
    ): List<ImagenReferenceImage> =
      generateMaskAndPadForOutpainting(image, newDimensions, newPosition, 0.01)
    /**
     * Generates two reference images of [ImagenRawImage] and [ImagenRawMask]. These images are
     * generated in this order:
     * * One [ImagenRawImage] containing the original image, padded out to the new dimensions with
     * black pixels, with the original image placed at the given placement
     * * One [ImagenRawMask] of the same dimensions containing white everywhere except at the
     * placement original image. This is the format expected by Imagen for outpainting requests.
     *
     * @param image the original image
     * @param newDimensions the new dimensions for outpainting. These new dimensions *must* be more
     * than the original image.
     * @param newPosition the placement of the original image within the new outpainted image.
     * @param dilation the dilation for the outpainting mask. See: [ImagenRawMask].
     */
    @JvmOverloads
    @JvmStatic
    public fun generateMaskAndPadForOutpainting(
      image: ImagenInlineImage,
      newDimensions: Dimensions,
      newPosition: ImagenImagePlacement = ImagenImagePlacement.CENTER,
      dilation: Double = 0.01
    ): List<ImagenReferenceImage> {
      val originalBitmap = image.asBitmap()
      if (
        originalBitmap.width > newDimensions.width || originalBitmap.height > newDimensions.height
      ) {
        throw IllegalArgumentException(
          "New Dimensions must be strictly larger than original image dimensions. Original image " +
            "is:${originalBitmap.width}x${originalBitmap.height}, new dimensions are " +
            "${newDimensions.width}x${newDimensions.height}"
        )
      }
      val normalizedPosition =
        newPosition.normalizeToDimensions(
          Dimensions(originalBitmap.width, originalBitmap.height),
          newDimensions,
        )

      if (normalizedPosition.x == null || normalizedPosition.y == null) {
        throw IllegalStateException("Error normalizing position for mask and padding.")
      }

      val normalizedImageRectangle =
        Rect(
          normalizedPosition.x,
          normalizedPosition.y,
          normalizedPosition.x + originalBitmap.width,
          normalizedPosition.y + originalBitmap.height,
        )

      val maskBitmap =
        Bitmap.createBitmap(newDimensions.width, newDimensions.height, Bitmap.Config.RGB_565)
      val newImageBitmap =
        Bitmap.createBitmap(newDimensions.width, newDimensions.height, Bitmap.Config.RGB_565)

      val maskCanvas = Canvas(maskBitmap)
      val newImageCanvas = Canvas(newImageBitmap)

      val blackPaint = Paint().apply { color = Color.BLACK }
      val whitePaint = Paint().apply { color = Color.WHITE }

      // Fill the mask with white, then draw a black rectangle where the image is.
      maskCanvas.drawPaint(whitePaint)
      maskCanvas.drawRect(normalizedImageRectangle, blackPaint)

      // fill the image with black, and then draw the bitmap into the corresponding spot
      newImageCanvas.drawPaint(blackPaint)
      newImageCanvas.drawBitmap(originalBitmap, null, normalizedImageRectangle, null)
      return listOf(
        ImagenRawImage(newImageBitmap.toImagenInlineImage()),
        ImagenRawMask(maskBitmap.toImagenInlineImage(), dilation),
      )
    }
  }
}

/**
 * A generated mask image which will auto-detect and mask out the background. The background will be
 * white, and the foreground black
 * @param dilation the amount to dilate the mask. This can help smooth the borders of an edit and
 * make it seem more convincing. For example, `0.05` will dilate the mask 5%.
 */
@PublicPreviewAPI
public class ImagenBackgroundMask(dilation: Double? = null) :
  ImagenMaskReference(maskConfig = ImagenMaskConfig(ImagenMaskMode.BACKGROUND, dilation)) {}

/**
 * A generated mask image which will auto-detect and mask out the foreground. The background will be
 * black, and the foreground white
 * @param dilation the amount to dilate the mask. This can help smooth the borders of an edit and
 * make it seem more convincing. For example, `0.05` will dilate the mask 5%.
 */
@PublicPreviewAPI
public class ImagenForegroundMask(dilation: Double? = null) :
  ImagenMaskReference(maskConfig = ImagenMaskConfig(ImagenMaskMode.FOREGROUND, dilation)) {}

/**
 * Represents a mask for Imagen editing. This image should contain only black and white pixels, with
 * black representing parts of the image which should not change.
 *
 * @param mask the mask image
 * @param dilation the amount to dilate the mask. This can help smooth the borders of an edit and
 * make it seem more convincing. For example, `0.05` will dilate the mask 5%.
 */
@PublicPreviewAPI
public class ImagenRawMask(mask: ImagenInlineImage, dilation: Double? = null) :
  ImagenMaskReference(
    maskConfig = ImagenMaskConfig(ImagenMaskMode.USER_PROVIDED, dilation),
    image = mask,
  ) {}

/**
 * Represents a generated mask for Imagen editing which masks out certain objects using object
 * detection.
 * @param classes the list of segmentation IDs for objects to detect and mask out. Find a
 * [list of segmentation IDs](https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/imagen-api-edit#segment-ids)
 * in the Vertex AI documentation.
 * @param dilation the amount to dilate the mask. This can help smooth the borders of an edit and
 * make it seem more convincing. For example, `0.05` will dilate the mask 5%.
 */
@PublicPreviewAPI
public class ImagenSemanticMask(classes: List<Int>, dilation: Double? = null) :
  ImagenMaskReference(maskConfig = ImagenMaskConfig(ImagenMaskMode.SEMANTIC, dilation, classes)) {}

/**
 * Represents a base image for Imagen editing
 * @param image the image
 */
@PublicPreviewAPI
public class ImagenRawImage(image: ImagenInlineImage) : ImagenReferenceImage(image = image) {}

/**
 * A reference image for style transfer
 * @param image the image representing the style you want to transfer to your original images
 * @param referenceId the reference ID you can use to reference this style in your prompt
 * @param description the description you can use to reference this style in your prompt
 */
@PublicPreviewAPI
public class ImagenStyleReference(
  image: ImagenInlineImage,
  referenceId: Int? = null,
  description: String? = null,
) :
  ImagenReferenceImage(
    image = image,
    referenceId = referenceId,
    styleConfig = ImagenStyleConfig(description)
  ) {}

/**
 * A reference image for generating an image with a specific subject
 * @param image the image of the subject
 * @param referenceId the reference ID you can use to reference this subject in your prompt
 * @param description the description you can use to reference this subject in your prompt
 * @param subjectType the type of the subject
 */
@PublicPreviewAPI
public class ImagenSubjectReference(
  image: ImagenInlineImage,
  referenceId: Int? = null,
  description: String? = null,
  subjectType: ImagenSubjectReferenceType? = null,
) :
  ImagenReferenceImage(
    image = image,
    referenceId = referenceId,
    subjectConfig = ImagenSubjectConfig(description, subjectType),
  ) {}
