package com.example.starter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import com.example.starter.pojo.Instance;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;

import static com.example.starter.Constants.INSTANCES_TABLE_NAME;
import static com.example.starter.Constants.REQUEST_TABLE_NAME;
import static com.example.starter.Constants.SCHEMA_NAME;

public class Dao {

  private Vertx vertx = Vertx.vertx();

  private static final String SAVE_INSTANCES_QUERY = "INSERT INTO " + SCHEMA_NAME + "." + INSTANCES_TABLE_NAME + " (instance_id, json, request_id) VALUES($1, $2, $3)";
  private static final String SAVE_REQUEST_ID = "INSERT INTO " + SCHEMA_NAME + "." + REQUEST_TABLE_NAME + " (request_id, last_updated_date) VALUES($1, $2)";

  public Future<Boolean> saveInstanceIds(List<Instance> instanceIds, String tenantId) {
    Promise<Boolean> promise = Promise.promise();
    List<Tuple> batch = instancesListToTuple(instanceIds);
    PgPool client = DBManager.getPool(this.vertx, tenantId);
    client.preparedQuery(SAVE_INSTANCES_QUERY)
      .executeBatch(batch, res -> {
        if (res.succeeded()) {
          promise.complete(true);
        } else {
          promise.fail(res.cause());
        }
      });
    return promise.future();
  }

  public Future<String> saveRequestId(String requestId, String tenantId) {
    Promise<String> promise = Promise.promise();
    OffsetDateTime dateTime = OffsetDateTime.now(ZoneId.systemDefault());

    PgPool client = DBManager.getPool(this.vertx, tenantId);
    client.preparedQuery(SAVE_REQUEST_ID)
      .execute(Tuple.of(requestId, dateTime), res -> {
        if (res.succeeded()) {
          promise.complete(requestId);
        } else {
          promise.fail(res.cause());
        }
      });
    return promise.future();
  }


  private List<Tuple> instancesListToTuple(List<Instance> instanceList) {
    return instanceList.stream()
      .map(elem -> Tuple.of(elem.getId().toString(), elem.getJsonb(), elem.getRequestId().toString()))
      .collect(Collectors.toList());
  }

}
