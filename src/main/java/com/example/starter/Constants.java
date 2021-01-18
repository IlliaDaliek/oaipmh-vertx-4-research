package com.example.starter;

import com.example.starter.util.Config;

public class Constants {

  public static final String SCHEMA_NAME;
  public static final String INSTANCES_TABLE_NAME = "instances";
  public static final String REQUEST_TABLE_NAME = "request_metadata_lb";

  public static final String OKAPI_URL = "X-Okapi-Url";
  public static final String OKAPI_TENANT = "X-Okapi-Tenant";
  public static final String OKAPI_TOKEN = "X-Okapi-Token";

  public static final String ISO_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  public static final String ISO_DATE_ONLY_PATTERN = "yyyy-MM-dd";

  static {
    SCHEMA_NAME = Config.OKAPI_TENANT + "_mod_oai_pmh";
  }

}
