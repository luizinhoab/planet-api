package io.starwars.planet.commons;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.HashSet;
import java.util.Set;

public abstract class RouterFactoryBase {

  private static final Logger logger = LoggerFactory.getLogger(RouterFactoryBase.class);

  public abstract void create(Vertx vertx, CircuitBreaker breaker, Handler<AsyncResult<Router>> handlerResult);

  public void enableCorsSupport(Router router) {

    logger.info("Enabling CORS support");

    Set<String> allowHeaders = new HashSet<>();
    allowHeaders.add("x-requested-with");
    allowHeaders.add("Access-Control-Allow-Origin");
    allowHeaders.add("origin");
    allowHeaders.add("Content-Type");
    allowHeaders.add("accept");
    Set<HttpMethod> allowMethods = new HashSet<>();
    allowMethods.add(HttpMethod.GET);
    allowMethods.add(HttpMethod.PUT);
    allowMethods.add(HttpMethod.OPTIONS);
    allowMethods.add(HttpMethod.POST);
    allowMethods.add(HttpMethod.DELETE);
    allowMethods.add(HttpMethod.PATCH);

    router.route().handler(CorsHandler.create("*")
      .allowedHeaders(allowHeaders)
      .allowedMethods(allowMethods));
  }
}
