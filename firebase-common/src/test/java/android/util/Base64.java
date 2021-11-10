package android.util;

public class Base64 {
  public static String encodeToString(byte[] input, int flags) {
    return java.util.Base64.getEncoder().encodeToString(input);
  }
}
