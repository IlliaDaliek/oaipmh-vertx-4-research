package com.example.starter;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

public class DBManager {

  private final static Map<String, PgPool> POOL_CACHE = new HashMap<>();
  private final static PgConnectOptions connectOptions = new PgConnectOptions();
  private final static PoolOptions poolOptions = new PoolOptions();

  static {
    connectOptions.setPort(5432)
      .setHost("localhost")
      .setDatabase("okapi_modules")
      .setUser("fs09000000_mod_oai_pmh")
      .setPassword("fs09000000");
    poolOptions.setMaxSize(10);
  }


  public static PgPool getPool(Vertx vertx, String tenantId) {
    if (POOL_CACHE.containsKey(tenantId)) {
      return POOL_CACHE.get(tenantId);
    }
    PgPool client = PgPool.pool(vertx, connectOptions, poolOptions);
    POOL_CACHE.put(tenantId, client);
    return client;
  }

}
