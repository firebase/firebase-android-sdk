package com.google.firebase.gradle.plugins

import java.io.*
import java.util.zip.ZipFile

object UnzipAar {
  /**
   * @param zipFilePath
   * @param destDirectory
   * @throws IOException
   */
  @Throws(IOException::class)
  fun unzip(zipFilePath: File, destDirectory: String) {

    File(destDirectory).run {
      if (!exists()) {
        mkdirs()
      }
    }

    ZipFile(zipFilePath).use { zip ->
      zip.entries().asSequence().forEach { entry ->
        zip.getInputStream(entry).use { input ->
          val filePath = destDirectory + File.separator + entry.name
          if (entry.name == "classes.jar") {
            extractFile(input, filePath)
          }
        }
      }
    }
  }

  /**
   * Extracts a zip entry (file entry)
   * @param inputStream
   * @param destFilePath
   * @throws IOException
   */
  @Throws(IOException::class)
  private fun extractFile(inputStream: InputStream, destFilePath: String) {
    val bos = BufferedOutputStream(FileOutputStream(destFilePath))
    val bytesIn = ByteArray(BUFFER_SIZE)
    var read: Int
    while (inputStream.read(bytesIn).also { read = it } != -1) {
      bos.write(bytesIn, 0, read)
    }
    bos.close()
  }

  /** Size of the buffer to read/write data */
  private const val BUFFER_SIZE = 4096
}
