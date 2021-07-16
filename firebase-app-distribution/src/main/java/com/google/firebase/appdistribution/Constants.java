package com.google.firebase.appdistribution;

class Constants {
  static class ErrorMessages {
    public static final String NETWORK_ERROR =
        "Failed to fetch releases due to unknown network error";

    public static final String JSON_PARSING_ERROR = "Error parsing service response";

    public static final String AUTHENTICATION_ERROR = "Failed to authenticate the tester";

    public static final String AUTHORIZATION_ERROR = "Failed to authorize the tester";

    public static final String NOT_FOUND_ERROR = "Tester or release not found";

    public static final String TIMEOUT_ERROR = "Failed to fetch releases due to timeout";

    public static final String UNKNOWN_ERROR = "Unknown Error";
  }
}
