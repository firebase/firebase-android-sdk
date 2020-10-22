// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Parser for the defaults XML file.
 *
 * <p>Firebase Remote Config (FRC) users can provide an XML file with a map of default values to be
 * used when no fetched values are available. This class helps parse that XML into a Java {@link
 * Map}.
 *
 * <p>The parser saves the texts of the {@code XML_TAG_KEY} and {@code XML_TAG_VALUE} tags inside
 * each {@code XML_TAG_ENTRY} as a key-value pair and returns a map of all such pairs.
 *
 * <p>For example, consider the following XML file:
 *
 * <pre>{@code
 * <defaults>
 *   <bad_entry>
 *     <key>first_default_key</key>
 *     <value>first_default_value</value>
 *   </bad_entry>
 *   <entry>
 *     <key>second_default_key</key>
 *     <value>second_default_value</value>
 *   </entry>
 *   <entry>
 *     <bad_key>third_default_key</bad_key>
 *     <value>third_default_value</value>
 *   </entry>
 *   <entry>
 *     <key>fourth_default_key</key>
 *     <bad_value>fourth_default_value</bad_value>
 *   </entry>
 * }</pre>
 *
 * Only the "second_default_key, second_default_value" pair would be recorded, since the remaining
 * tags are malformed.
 *
 * @author Miraziz Yusupov
 */
public class DefaultsXmlParser {
  private static final String XML_TAG_ENTRY = "entry";
  private static final String XML_TAG_KEY = "key";
  private static final String XML_TAG_VALUE = "value";

  /**
   * Returns a {@link Map} of default FRC values parsed from the defaults XML file.
   *
   * @param context the application context.
   * @param resourceId the resource id of the defaults XML file.
   */
  public static Map<String, String> getDefaultsFromXml(Context context, int resourceId) {
    Map<String, String> defaultsMap = new HashMap<>();

    try {
      Resources resources = context.getResources();
      if (resources == null) {
        Log.e(
            TAG,
            "Could not find the resources of the current context "
                + "while trying to set defaults from an XML.");
        return defaultsMap;
      }

      XmlResourceParser xmlParser = resources.getXml(resourceId);

      String curTag = null;
      String key = null;
      String value = null;

      int eventType = xmlParser.getEventType();
      while (eventType != XmlResourceParser.END_DOCUMENT) {
        if (eventType == XmlResourceParser.START_TAG) {
          curTag = xmlParser.getName();
        } else if (eventType == XmlResourceParser.END_TAG) {
          if (xmlParser.getName().equals(XML_TAG_ENTRY)) {
            if (key != null && value != null) {
              defaultsMap.put(key, value);
            } else {
              Log.w(TAG, "An entry in the defaults XML has an invalid key and/or value tag.");
            }
            key = null;
            value = null;
          }
          curTag = null;
        } else if (eventType == XmlResourceParser.TEXT) {
          if (curTag != null) {
            switch (curTag) {
              case XML_TAG_KEY:
                key = xmlParser.getText();
                break;
              case XML_TAG_VALUE:
                value = xmlParser.getText();
                break;
              default:
                Log.w(TAG, "Encountered an unexpected tag while parsing the defaults XML.");
                break;
            }
          }
        }
        eventType = xmlParser.next();
      }
    } catch (XmlPullParserException | IOException e) {
      Log.e(TAG, "Encountered an error while parsing the defaults XML file.", e);
    }
    return defaultsMap;
  }
}
