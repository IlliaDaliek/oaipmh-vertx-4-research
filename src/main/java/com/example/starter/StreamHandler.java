package com.example.starter;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.util.ReflectionUtils;

import com.example.starter.pojo.Instance;
import com.example.starter.pojo.Request;
import com.example.starter.util.Config;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.parsetools.JsonEvent;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.parsetools.impl.JsonParserImpl;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.impl.Connection;

import static com.example.starter.util.Config.REQUEST_TIMEOUT;
import static com.example.starter.util.Util.appendHeadersAndSetTimeout;
import static com.example.starter.util.Util.buildInventoryQuery;
import static com.example.starter.util.Util.buildRequest;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class StreamHandler implements Handler<RoutingContext> {

  private static final Logger logger = LogManager.getLogger(StreamHandler.class);

  private static final int DATABASE_FETCHING_CHUNK_SIZE = 50;

  private static final String INSTANCE_ID_FIELD_NAME = "instanceId";
  private static final String ERROR_FROM_STORAGE = "Got error response from %s, uri: '%s' message: %s";

  private Dao dao = new Dao();

  @Override
  public void handle(RoutingContext event) {
    logger.debug("Request started to be processed");
    Promise<Void> globalPromise = Promise.promise();
    Vertx vertx = event.vertx();
    Context vertxContext = vertx.getOrCreateContext();
    Request request = buildRequest(event);
    String requestId = UUID.randomUUID().toString();
    Future<String> future = dao.saveRequestId(requestId, Config.OKAPI_TENANT);
    future.compose(res -> {
      Promise<Void> fetchingIdsPromise = createBatchStream(globalPromise, request, vertxContext, requestId);
      return fetchingIdsPromise.future();
    }).onComplete(v -> {
      if(globalPromise.future().isComplete()) {
        Future<Void> voidFuture = globalPromise.future();
        if(voidFuture.succeeded()) {
          writeResponse(event, HttpResponseStatus.OK.code(), "good");
        } else {
          writeResponse(event, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), voidFuture.cause().getMessage());
        }
        return;
      }
      if(v.succeeded()) {
        writeResponse(event, HttpResponseStatus.OK.code(), "good");
      } else {
        writeResponse(event, HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), v.cause().getMessage());
      }
      globalPromise.complete();
    });
  }

  private void writeResponse(RoutingContext routingContext, int statusCode, String body) {
    routingContext.response().putHeader(CONTENT_TYPE, TEXT_PLAIN).setStatusCode(statusCode).end(body);
  }

  private Promise<Void> createBatchStream(Promise<Void> globalPromise, Request request, Context vertxContext, String requestId) {
    Promise<Void> promise = Promise.promise();
    final HttpClientOptions httpClientOptions = new HttpClientOptions();
    httpClientOptions.setKeepAliveTimeout(REQUEST_TIMEOUT);
    httpClientOptions.setConnectTimeout(REQUEST_TIMEOUT);
    httpClientOptions.setIdleTimeout(REQUEST_TIMEOUT);
    HttpClient httpClient = vertxContext.owner().createHttpClient(httpClientOptions);
    buildInventoryQuery(httpClient, request)
      .onSuccess(httpClientRequest -> {
        appendHeadersAndSetTimeout(request, httpClientRequest);
        BatchStreamWrapper databaseWriteStream = getBatchHttpStream(globalPromise, vertxContext);
        httpClientRequest.exceptionHandler(e -> {
          logger.error(e.getMessage(), e);
          handleException(globalPromise, e);
        });
        httpClientRequest.send()
          .onSuccess(response -> writeResponseToStream(httpClient, globalPromise, httpClientRequest,
            databaseWriteStream, response))
          .onFailure(e -> {
            logger.error(e.getMessage());
            globalPromise.fail(e);
          });
        AtomicReference<ArrayDeque<Promise<Connection>>> queue = new AtomicReference<>();
        try {
          queue.set(getWaitersQueue(vertxContext.owner(), request));
        } catch (IllegalStateException ex) {
          logger.error(ex.getMessage());
          globalPromise.fail(ex);
        }

        databaseWriteStream.setCapacityChecker(() -> queue.get().size() > 20);

        databaseWriteStream.handleBatch(batch -> {
          Promise<Void> savePromise = saveInstancesIds(batch, request, requestId, databaseWriteStream);
          final Long returnedCount = databaseWriteStream.getReturnedCount();

          if (returnedCount % 1000 == 0) {
            logger.info("Batch saving progress: " + returnedCount + " returned so far, batch size: " + batch.size() + ", http ended: " + databaseWriteStream.isStreamEnded());
          }

          if (databaseWriteStream.isTheLastBatch()) {
            savePromise.future().toCompletionStage().thenRun(promise::complete);
          }
          databaseWriteStream.invokeDrainHandler();
        });
      })
      .onFailure(e -> {
        logger.error(e.getMessage());
        globalPromise.fail(e);
      });
    return promise;
  }

  private void writeResponseToStream(HttpClient inventoryHttpClient, Promise<?> promise, HttpClientRequest inventoryQuery, BatchStreamWrapper databaseWriteStream, HttpClientResponse resp) {
    if (resp.statusCode() != 200) {
      String errorMsg = format(ERROR_FROM_STORAGE, "inventory-storage", inventoryQuery.absoluteURI(), resp.statusMessage());
      promise.fail(new IllegalStateException(errorMsg));
    } else {
      JsonParser jp = new JsonParserImpl(resp);
      jp.objectValueMode();
      jp.pipeTo(databaseWriteStream);
      jp.endHandler(e -> {
        databaseWriteStream.end();
        inventoryHttpClient.close();
      })
        .exceptionHandler(throwable -> {
          logger.error("Error has been occurred at JsonParser while reading data from response. Message:{}", throwable.getMessage(), throwable);
          databaseWriteStream.end();
          inventoryHttpClient.close();
          promise.fail(throwable);
        });
    }
  }

  private BatchStreamWrapper getBatchHttpStream(Promise<?> promise, Context vertxContext) {
    final Vertx vertx = vertxContext.owner();
    BatchStreamWrapper databaseWriteStream = new BatchStreamWrapper(vertx, DATABASE_FETCHING_CHUNK_SIZE);
    databaseWriteStream.exceptionHandler(e -> {
      if (e != null) {
        handleException(promise, e);
      }
    });
    return databaseWriteStream;
  }

  private Promise<Void> saveInstancesIds(List<JsonEvent> instances, Request request, String requestId, BatchStreamWrapper databaseWriteStream) {
    Promise<Void> promise = Promise.promise();
    if (instances.isEmpty()) {
      logger.debug("Skip saving instances. Instances list is empty.");
      promise.complete();
      return promise;
    }
    List<Instance> instancesList = instances.stream().map(JsonEvent::objectValue).map(inst ->
      new Instance().withId(UUID.fromString(inst.getString(INSTANCE_ID_FIELD_NAME)))
        .withJsonb(inst.toString())
        .withRequestId(UUID.fromString(requestId))
    ).collect(Collectors.toList());
    dao.saveInstanceIds(instancesList, request.getTenant()).onComplete(res -> {
      if (res.failed()) {
        logger.error("Cannot saving ids, error from database: " + res.cause().getMessage(), res.cause());
        promise.fail(res.cause());
      } else {
        promise.complete();
        databaseWriteStream.invokeDrainHandler();
      }
    });
    return promise;
  }

  private ArrayDeque<Promise<Connection>> getWaitersQueue(Vertx vertx, Request request) {
    PgPool pgPool = DBManager.getPool(vertx, request.getTenant());
    if (Objects.nonNull(pgPool)) {
      try {
        return (ArrayDeque<Promise<Connection>>) getValueFrom(getValueFrom(pgPool, "pool"), "waiters");
      } catch (NullPointerException ex) {
        throw new IllegalStateException("Cannot get the pool size. Object for retrieving field is null.");
      }
    } else {
      throw new IllegalStateException("Cannot obtain the pool. Pool is null.");
    }
  }

  private Object getValueFrom(Object obj, String fieldName) {
    Field field = requireNonNull(ReflectionUtils.findField(requireNonNull(obj.getClass()), fieldName));
    ReflectionUtils.makeAccessible(field);
    return ReflectionUtils.getField(field, obj);
  }

  private void handleException(Promise<?> promise, Throwable cause) {
    logger.error("Error occurred " + cause.getMessage());
    promise.fail(cause);
  }

}
