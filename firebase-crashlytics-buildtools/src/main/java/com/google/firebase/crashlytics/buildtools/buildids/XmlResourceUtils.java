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

package com.google.firebase.crashlytics.buildtools.buildids;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Helper class for reading / writing Android XML resource files. */
public class XmlResourceUtils {

  private static final String LIB_ATTRIBUTE = "com.google.firebase.crashlytics.build_ids_lib";

  private static final String ARCH_ATTRIBUTE = "com.google.firebase.crashlytics.build_ids_arch";

  private static final String BUILD_ID_ATTRIBUTE =
      "com.google.firebase.crashlytics.build_ids_build_id";

  /** String for the "name" attribute in a string resource. */
  private static final String XML_NAME_ATTRIBUTE = "name";

  private static final String XML_ITEM_TAG = "item";
  private static final String XML_ARRAY_TAG = "string-array";

  /**
   * Format string for the array
   *
   * Example
   *   <array name="com.google.firebase.crashlytics.build_id_list"></array>
   */
  static final String ARRAY_RESOURCE_FORMAT =
      "<"
          + XML_ARRAY_TAG
          + " "
          + XML_NAME_ATTRIBUTE
          + "=\"%s\""
          + " tools:ignore=\"UnusedResources,TypographyDashes\" translatable=\"false\""
          + ">\n%s</"
          + XML_ARRAY_TAG
          + ">\n";

  /**
   * Format string for an item
   */
  static final String ITEM_RESOURCE_FORMAT = "<" + XML_ITEM_TAG + ">%s</" + XML_ITEM_TAG + ">\n";

  /**
   * Returns the string element containing the mapping file id, or null if it does not exist as a
   * resource in the given document.
   */
  public static List<BuildIdInfo> getBuildIds(Document doc) {
    List<BuildIdInfo> buildIdInfoList = new ArrayList<>();

    NodeList libs = getLibElement(doc).getElementsByTagName(XML_ITEM_TAG);
    NodeList archs = getArchElement(doc).getElementsByTagName(XML_ITEM_TAG);
    NodeList buildIds = getBuildIdsElement(doc).getElementsByTagName(XML_ITEM_TAG);
    for (int i = 0; i < buildIds.getLength(); i++) {
      String lib = libs.item(i).getTextContent();
      String arch = archs.item(i).getTextContent();
      String buildId = buildIds.item(i).getTextContent();
      buildIdInfoList.add(new BuildIdInfo(lib, arch, buildId));
    }
    return buildIdInfoList;
  }

  private static Element getLibElement(Document doc) {
    return getResourceElement(doc, LIB_ATTRIBUTE);
  }

  private static Element getArchElement(Document doc) {
    return getResourceElement(doc, ARCH_ATTRIBUTE);
  }

  private static Element getBuildIdsElement(Document doc) {
    return getResourceElement(doc, BUILD_ID_ATTRIBUTE);
  }

  public static Element getResourceElement(Document doc, String elementName) {
    NodeList strings = doc.getElementsByTagName(XML_ARRAY_TAG);
    Element element = null;
    for (int i = 0; i < strings.getLength(); ++i) {
      Element el = (Element) strings.item(i);
      if (el.hasAttribute(XML_NAME_ATTRIBUTE)
          && el.getAttribute(XML_NAME_ATTRIBUTE).equals(elementName)) {
        element = el;
        break;
      }
    }

    return element;
  }

  private static String formatLibItems(List<BuildIdInfo> buildIdInfoList) {
    StringBuilder items = new StringBuilder();
    for (BuildIdInfo buildIdInfo : buildIdInfoList) {
      items.append(
          String.format(XmlResourceUtils.ITEM_RESOURCE_FORMAT, buildIdInfo.getLibraryName()));
    }
    return items.toString();
  }

  private static String formatArchItems(List<BuildIdInfo> buildIdInfoList) {
    StringBuilder items = new StringBuilder();
    for (BuildIdInfo buildIdInfo : buildIdInfoList) {
      items.append(String.format(XmlResourceUtils.ITEM_RESOURCE_FORMAT, buildIdInfo.getArch()));
    }
    return items.toString();
  }

  private static String formatBuildIdItems(List<BuildIdInfo> buildIdInfoList) {
    StringBuilder items = new StringBuilder();
    for (BuildIdInfo buildIdInfo : buildIdInfoList) {
      items.append(String.format(XmlResourceUtils.ITEM_RESOURCE_FORMAT, buildIdInfo.getBuildId()));
    }
    return items.toString();
  }

  /**
   * Returns an InputStream whose contents are a valid Android resource xml file containing
   * the given mapping file id as a string resource.
   */
  public static InputStream createResourceFileStream(List<BuildIdInfo> buildIdInfoList) {
    String xml =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources xmlns:tools=\"http://schemas.android.com/tools\">\n"
            + "<!--\n"
            + "  This file is automatically generated by Crashlytics to uniquely\n"
            + "  identify the build ids for your Android application.\n"
            + "\n"
            + "  Do NOT modify or commit to source control!\n"
            + "-->\n"
            + String.format(
                XmlResourceUtils.ARRAY_RESOURCE_FORMAT,
                XmlResourceUtils.LIB_ATTRIBUTE,
                formatLibItems(buildIdInfoList))
            + String.format(
                XmlResourceUtils.ARRAY_RESOURCE_FORMAT,
                XmlResourceUtils.ARCH_ATTRIBUTE,
                formatArchItems(buildIdInfoList))
            + String.format(
                XmlResourceUtils.ARRAY_RESOURCE_FORMAT,
                XmlResourceUtils.BUILD_ID_ATTRIBUTE,
                formatBuildIdItems(buildIdInfoList))
            + "</resources>\n";

    return new ByteArrayInputStream(xml.getBytes());
  }
}
