package com.example.starter.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import com.example.starter.pojo.Request;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.RoutingContext;

import static com.example.starter.Constants.ISO_DATE_TIME_PATTERN;
import static com.example.starter.Constants.OKAPI_TENANT;
import static com.example.starter.Constants.OKAPI_TOKEN;
import static com.example.starter.util.Config.REQUEST_TIMEOUT;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.vertx.core.http.HttpHeaders.ACCEPT;
import static java.lang.String.format;

public class Util {

  private static final String DATE_ONLY_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";
  private static final String[] dateFormats = {
    org.apache.commons.lang.time.DateFormatUtils.ISO_DATE_FORMAT.getPattern(),
    ISO_DATE_TIME_PATTERN
  };

  private static final String INVENTORY_UPDATED_INSTANCES_ENDPOINT = "/inventory-hierarchy/updated-instance-ids";
  private static final String SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS = "skipSuppressedFromDiscoveryRecords";
  private static final String DELETED_RECORD_SUPPORT_PARAM_NAME = "deletedRecordSupport";
  private static final String START_DATE_PARAM_NAME = "startDate";
  private static final String END_DATE_PARAM_NAME = "endDate";

  private static final DateFormat dateFormat = new SimpleDateFormat(ISO_DATE_TIME_PATTERN);

  public static Request buildRequest(RoutingContext routingContext) {
    MultiMap map = routingContext.request().params();
    return new Request().setFrom(map.get("from"))
      .setUntil(map.get("until"))
      .setDeletedSupport(true)
      .setSuppressFromDiscovery(true)
      .setOkapiUrl(Config.OKAPI_URL)
      .setOkapiToken(Config.OKAPI_TOKEN)
      .setTenant(Config.OKAPI_TENANT);
  }

  public static Future<HttpClientRequest> buildInventoryQuery(HttpClient httpClient, Request request) {
    Map<String, String> paramMap = new HashMap<>();
    Date date = convertStringToDate(request.getFrom(), false, false);
    if (date != null) {
      paramMap.put(START_DATE_PARAM_NAME, dateFormat.format(date));
    }
    date = convertStringToDate(request.getUntil(), true, false);
    if (date != null) {
      paramMap.put(END_DATE_PARAM_NAME, dateFormat.format(date));
    }
    paramMap.put(DELETED_RECORD_SUPPORT_PARAM_NAME, String.valueOf(request.isDeletedSupport()));
    paramMap.put(SKIP_SUPPRESSED_FROM_DISCOVERY_RECORDS, String.valueOf(request.isSuppressFromDiscovery()));

    final String params = paramMap.entrySet().stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining("&"));

    String inventoryQuery = format("%s?%s", INVENTORY_UPDATED_INSTANCES_ENDPOINT, params);
    RequestOptions requestOptions = new RequestOptions();
    requestOptions.setAbsoluteURI(request.getOkapiUrl() + inventoryQuery);
    if (request.getOkapiUrl().contains("https")) {
      requestOptions.setSsl(true);
    }
    requestOptions.setMethod(HttpMethod.GET);
    return httpClient.request(requestOptions);
  }

  public static void appendHeadersAndSetTimeout(Request request, HttpClientRequest httpClientRequest) {
    httpClientRequest.putHeader(OKAPI_TOKEN, request.getOkapiToken());
    httpClientRequest.putHeader(OKAPI_TENANT, request.getTenant());
    httpClientRequest.putHeader(ACCEPT, APPLICATION_JSON);
    httpClientRequest.setTimeout(REQUEST_TIMEOUT);
  }

  public static Date convertStringToDate(String dateTimeString, boolean shouldCompensateUntilDate, boolean shouldEqualizeTimeBetweenZones) {
    try {
      if (StringUtils.isEmpty(dateTimeString)) {
        return null;
      }
      Date date = DateUtils.parseDate(dateTimeString, dateFormats);
      if (shouldCompensateUntilDate) {
        if (dateTimeString.matches(DATE_ONLY_PATTERN)) {
          date = DateUtils.addDays(date, 1);
        } else {
          date = DateUtils.addSeconds(date, 1);
        }
      }
      if (shouldEqualizeTimeBetweenZones) {
        return addTimeDiffBetweenCurrentTimeZoneAndUTC(date);
      }
      return date;
    } catch (DateTimeParseException | ParseException e) {
      return null;
    }
  }

  private static Date addTimeDiffBetweenCurrentTimeZoneAndUTC(Date date) {
    int secondsDiff = ZoneId.systemDefault()
      .getRules()
      .getOffset(date.toInstant())
      .getTotalSeconds();
    Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
    calendar.setTime(date);
    calendar.add(Calendar.SECOND, secondsDiff);
    return calendar.getTime();
  }

}
