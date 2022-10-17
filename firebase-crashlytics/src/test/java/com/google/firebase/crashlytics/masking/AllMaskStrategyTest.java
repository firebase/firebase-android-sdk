package com.google.firebase.crashlytics.masking;

import static org.junit.Assert.*;

import org.junit.Test;

public class AllMaskStrategyTest {
  @Test
  public void testGetMaskedMessage_allMask_defaultPlaceHolder() {
    final AllMaskStrategy maskStrategy = new AllMaskStrategy();
    assertEquals("*******", maskStrategy.getMaskedMessage("abcdefg"));
  }

  @Test
  public void testGetMaskedMessage_allMask_customPlaceHolder() {
    final AllMaskStrategy maskStrategy = new AllMaskStrategy("-");
    assertEquals("-------", maskStrategy.getMaskedMessage("abcdefg"));
  }

  @Test
  public void testGetMaskedMessage_allMask_originalMessageIncludesPlaceHolderCharacter() {
    final AllMaskStrategy maskStrategy = new AllMaskStrategy("-");
    assertEquals("-------", maskStrategy.getMaskedMessage("abc-efg"));
  }
}
