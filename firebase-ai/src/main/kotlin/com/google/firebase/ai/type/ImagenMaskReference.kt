package com.google.firebase.ai.type

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

@PublicPreviewAPI
public abstract class ImagenMaskReference
internal constructor(maskConfig: ImagenMaskConfig, image: ImagenInlineImage? = null) :
  ImagenReferenceImage(maskConfig = maskConfig, image = image) {

  public companion object {
    public fun generateMaskAndPadForOutpainting(
      image: ImagenInlineImage,
      newDimensions: Dimensions,
      newPosition: ImagenImagePlacement = ImagenImagePlacement.CENTER,
    ): List<ImagenReferenceImage> {
      val originalBitmap = image.asBitmap()
      val normalizedPosition =
        newPosition.normalizeToDimensions(
          Dimensions(originalBitmap.width, originalBitmap.height),
          newDimensions,
        )
      val normalizedImageRectangle =
        Rect(
          normalizedPosition.x!!,
          normalizedPosition.y!!,
          normalizedPosition.x + originalBitmap.width,
          normalizedPosition.y + originalBitmap.height,
        )

      val maskBitmap =
        Bitmap.createBitmap(newDimensions.width, newDimensions.height, Bitmap.Config.RGB_565)
      val newImageBitmap =
        Bitmap.createBitmap(newDimensions.width, newDimensions.height, Bitmap.Config.RGB_565)

      val maskCanvas = Canvas(maskBitmap)
      val newImageCanvas = Canvas(newImageBitmap)

      val blackPaint = Paint()
      blackPaint.color = Color.BLACK
      val whitePaint = Paint()
      whitePaint.color = Color.WHITE

      // Fill the mask with white, then draw a black rectangle where the image is.
      maskCanvas.drawPaint(whitePaint)
      maskCanvas.drawRect(normalizedImageRectangle, blackPaint)

      // fill the image with black, and then draw the bitmap into the corresponding spot
      newImageCanvas.drawPaint(blackPaint)
      newImageCanvas.drawBitmap(originalBitmap, null, normalizedImageRectangle, null)
      return listOf(
        ImagenRawImage(newImageBitmap.toImagenInlineImage()),
        ImagenRawMask(maskBitmap.toImagenInlineImage()),
      )
    }
  }
}
