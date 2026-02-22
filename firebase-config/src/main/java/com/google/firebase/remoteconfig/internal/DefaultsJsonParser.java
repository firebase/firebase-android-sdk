package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Parser for the defaults JSON file.
 *
 * <p>Firebase Remote Config (FRC) users can provide a JSON file with a map of default values to be
 * used when no fetched values are available. This class helps parse that JSON into a Java {@link
 * Map}.
 *
 * <p>The parser saves the key-value pairs directly from the JSON file and returns a map of all such pairs.
 *
 * <p>For example, consider the following JSON file:
 *
 * <pre>{@code
 * {
 *   "first_default_key": "first_default_value",
 *   "second_default_key": "second_default_value",
 *   "third_default_key": "third_default_value"
 * }
 * }</pre>
 *
 * The parser would return a map with the following key-value pairs:
 * <ul>
 *   <li>"first_default_key" -> "first_default_value"</li>
 *   <li>"second_default_key" -> "second_default_value"</li>
 *   <li>"third_default_key" -> "third_default_value"</li>
 * </ul>
 *
 * @author Laurentiu Rosu
 */
public class DefaultsJsonParser {
    /**
     * Returns a {@link Map} of default FRC values parsed from the defaults JSON file.
     *
     * @param context the application context.
     * @param resourceId the resource id of the defaults JSON file.
     */
    public static Map<String, String> getDefaultsFromJson(Context context, int resourceId) {
        Map<String, String> defaultsMap = new HashMap<>();

        try {
            Resources resources = context.getResources();
            if (resources == null) {
                Log.e(TAG, "Could not find the resources of the current context " +
                        "while trying to set defaults from a JSON.");
                return defaultsMap;
            }

            InputStream inputStream = resources.openRawResource(resourceId);
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            inputStream.close();
            String jsonString = new String(buffer, StandardCharsets.UTF_8);

            JSONObject jsonObject = new JSONObject(jsonString);

            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = jsonObject.getString(key);
                defaultsMap.put(key, value);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Encountered an error while trying to parse the defaults JSON file.", e);
            return new HashMap<>();
        }
        return defaultsMap;
    }
}
