package io.starwars.commons;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Optional;

public class RoutingBase {

  private static final Logger logger = LoggerFactory.getLogger(RoutingBase.class);
  private static final  int OK = 200;

  public void sendSuccess(RoutingContext context, String body){
    logger.debug("{} - {} - {}", context.request().method(), context.request().uri(), OK);
    context.response()
      .setStatusCode(200)
      .setChunked(true)
      .write(body)
      .end();
  }

  public void routeFailures(Router router){
    router.route("/*").failureHandler(context -> {

      Optional.ofNullable(context.failure()).ifPresent(t -> {
        logger.error(t);
      });

      var code = context.statusCode() == -1 ? 500: context.statusCode();
      var m = context.get("message");
      var message = m != null?m:HttpResponseStatus.valueOf(code).reasonPhrase();

      JsonObject json = new JsonObject()
        .put("timestamp", System.currentTimeMillis())
        .put("message", message)
        .put("path", context.request().path());
      logger.debug("{} - {} - {} - {}", context.request().method(), context.request().uri(), code, message);
      context.response().setStatusCode(code)
        .putHeader(HttpHeaders.CONTENT_TYPE,"application/json; charset=utf-8")
        .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(json.encodePrettily().length()))
        .end(json.encodePrettily());
    });
  }
}
