/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.crashlytics.buildtools.mappingfiles;

import com.google.firebase.crashlytics.buildtools.Buildtools;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public abstract class MappingFileIdReader {

  protected final DocumentBuilder docBuilder;

  private static class FileIdReader extends MappingFileIdReader {
    private final File file;

    FileIdReader(File xmlFile, DocumentBuilder docBuilder) {
      super(docBuilder);
      this.file = xmlFile;
    }

    @Override
    protected Document parseXmlSource() throws IOException, SAXException {
      if (!file.exists()) {
        return null;
      }
      return getDocumentBuilder().parse(file);
    }
  }

  private static class StringIdReader extends MappingFileIdReader {
    private final String string;

    StringIdReader(String xmlString, DocumentBuilder docBuilder) {
      super(docBuilder);
      this.string = xmlString;
    }

    @Override
    protected Document parseXmlSource() throws IOException, SAXException {
      if (string == null || string.isEmpty()) {
        return null;
      }
      ByteArrayInputStream stream =
          new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
      return getDocumentBuilder().parse(stream);
    }
  }

  /**
   * Factory method to create a MappingFileIdReader that can parse the id from an Android
   * resource file.
   */
  public static MappingFileIdReader create(File resourceFile) {
    try {
      return new FileIdReader(
          resourceFile, DocumentBuilderFactory.newInstance().newDocumentBuilder());
    } catch (ParserConfigurationException e) {
      Buildtools.logE("Crashlytics experienced an unrecoverable parser configuration exception", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Factory method to create an MappingFileIdReader that can parse the id from string contents
   * of an Android resource file.
   */
  public static MappingFileIdReader create(String xmlString) {
    try {
      return new StringIdReader(
          xmlString, DocumentBuilderFactory.newInstance().newDocumentBuilder());
    } catch (ParserConfigurationException e) {
      Buildtools.logE("Crashlytics experienced an unrecoverable parser configuration exception", e);
      throw new RuntimeException(e);
    }
  }

  protected abstract Document parseXmlSource() throws SAXException, IOException;

  protected MappingFileIdReader(DocumentBuilder builder) {
    docBuilder = builder;
  }

  protected DocumentBuilder getDocumentBuilder() {
    return this.docBuilder;
  }

  /**
   * Parses the resource files and returns the mapping file id, or null if there is no element
   * representing the mapping file id. ALWAYS reads from the file, never caches.
   *
   * @throws java.io.IOException if the file could not be opened OR if there was a parsing error.
   */
  public String getMappingFileId() throws IOException {
    String toReturn = null;
    try {
      Document doc = parseXmlSource();
      if (doc == null) {
        return null;
      }
      Element idElement = XmlResourceUtils.getMappingFileIdElement(doc);
      if (idElement != null) {
        toReturn = idElement.getTextContent();
      }
    } catch (SAXException e) {
      // wrap the SAXException so we don't expose the XML details to calling methods.
      throw new IOException(e);
    }
    return toReturn;
  }
}
