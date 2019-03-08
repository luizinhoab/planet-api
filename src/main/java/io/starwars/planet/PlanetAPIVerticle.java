package io.starwars.planet;

import io.starwars.commons.HttpServerVerticleBase;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

public class PlanetAPIVerticle extends HttpServerVerticleBase {

  private static final Logger logger = LoggerFactory.getLogger(PlanetAPIVerticle.class);

 private JsonObject apiConfig;
 private String apiName;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);


    this.apiConfig = Optional.ofNullable(config().getJsonObject("api")).orElse(new JsonObject());

    this.apiName = apiConfig.getString("name", "planet-api");

  }

  @Override
  public void start(Future<Void> startFuture) {
    logger.info("Startinf api verticle");
    buildRouter(event -> {
      if (event.succeeded()) {
        super.start(startFuture);
        publishHttpEndpoint(this.apiName, this.host, this.port, startFuture);
      }else{
        startFuture.fail(startFuture.cause());
      }
    });
  }

  private void buildRouter(Handler<AsyncResult<Void>> resultHandler){
    logger.info("Building Router");
    new PlanetRouterFactory().create(vertx, getCircuitBreaker(), asyncRouter -> {
      this.apiName = apiConfig.getString("name", "planet-api");
      if (asyncRouter.succeeded()) {
        this.router = asyncRouter.result();
        resultHandler.handle(Future.succeededFuture());
      }else{
        logger.error("Was not possible obtain router", asyncRouter.cause());
        resultHandler.handle(Future.failedFuture(asyncRouter.cause()));
      }
    });

  }

}
