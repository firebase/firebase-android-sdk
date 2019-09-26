// Copyright 2018 Google LLC
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

package com.google.firebase.dynamiclinks.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;

import android.net.Uri;
import android.os.Parcel;
import com.google.firebase.dynamiclinks.internal.ShortDynamicLinkImpl.WarningImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Test {@link com.google.firebase.dynamiclinks.internal.ShortDynamicLinkImpl}. */
@RunWith(RobolectricTestRunner.class)
public class ShortDynamicLinkImplTest {

  private static final String SHORT_LINK_STRING = "https://short.link";
  private static final String PREVIEW_LINK_STRING = "https://preview.link";
  private static final int NUM_WARNINGS = 12;
  private static final String WARNING_MESSAGE = "warningMessage";

  private Uri shortLink;
  private Uri previewLink;

  private static List<WarningImpl> createWarningList() {
    List<WarningImpl> warnings = new ArrayList<>();
    for (int i = 0; i < NUM_WARNINGS; ++i) {
      warnings.add(new WarningImpl(WARNING_MESSAGE + i));
    }
    return warnings;
  }

  @Before
  public void setUp() {
    shortLink = Uri.parse(SHORT_LINK_STRING);
    previewLink = Uri.parse(PREVIEW_LINK_STRING);
  }

  @Test
  public void testConstructor() {
    List<WarningImpl> warnings = createWarningList();
    ShortDynamicLinkImpl shortDynamicLink =
        new ShortDynamicLinkImpl(shortLink, previewLink, warnings);

    assertEquals(shortLink, shortDynamicLink.getShortLink());
    assertEquals(previewLink, shortDynamicLink.getPreviewLink());
    assertEquals(warnings, shortDynamicLink.getWarnings());
  }

  @Test
  public void testWriteToParcel() {
    List<WarningImpl> warnings = createWarningList();
    ShortDynamicLinkImpl shortDynamicLink =
        new ShortDynamicLinkImpl(shortLink, previewLink, warnings);

    Parcel out = Parcel.obtain();
    shortDynamicLink.writeToParcel(out, 0);
    out.setDataPosition(0);

    ShortDynamicLinkImpl parcelLink = ShortDynamicLinkImpl.CREATOR.createFromParcel(out);

    assertEquals(shortDynamicLink.getShortLink(), parcelLink.getShortLink());
    assertEquals(shortDynamicLink.getPreviewLink(), parcelLink.getPreviewLink());
    List<WarningImpl> parcelWarnings = parcelLink.getWarnings();
    assertEquals(warnings.size(), parcelWarnings.size());
    for (int i = 0; i < warnings.size(); ++i) {
      WarningImpl warning = warnings.get(i);
      WarningImpl parcelWarning = parcelWarnings.get(i);
      assertEquals(warning.getCode(), parcelWarning.getCode());
      assertEquals(warning.getMessage(), parcelWarning.getMessage());
    }
  }

  @Test
  public void testWarningImpl_Constructor() {
    WarningImpl warning = new WarningImpl(WARNING_MESSAGE);

    assertNull(warning.getCode());
    assertEquals(WARNING_MESSAGE, warning.getMessage());
  }

  @Test
  public void testContactMethod_WriteToParcel() {
    WarningImpl warning = new WarningImpl(WARNING_MESSAGE);

    Parcel out = Parcel.obtain();
    warning.writeToParcel(out, 0);
    out.setDataPosition(0);

    WarningImpl parcelWarning = WarningImpl.CREATOR.createFromParcel(out);

    assertEquals(warning.getCode(), parcelWarning.getCode());
    assertEquals(warning.getMessage(), parcelWarning.getMessage());
  }
}
