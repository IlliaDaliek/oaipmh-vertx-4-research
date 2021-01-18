package com.example.starter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class MainVerticle extends AbstractVerticle {

  private static final Logger logger = LogManager.getLogger(StreamHandler.class);

  private static final String URI = "/test";
  private final Handler<RoutingContext> handler;

  public MainVerticle() {
    this.handler = new StreamHandler();
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Router router = Router.router(vertx);
    router.get(URI)
      .handler(handler);

    HttpServerOptions serverOptions = new HttpServerOptions().setPort(7596);
    HttpServer server = vertx.createHttpServer(serverOptions);
    server.requestHandler(router)
      .listen(7596, http -> {
        if (http.succeeded()) {
          startPromise.complete();
          logger.debug("HTTP server started on port " + server.actualPort());
        } else {
          startPromise.fail(http.cause());
        }
      });
  }
}
