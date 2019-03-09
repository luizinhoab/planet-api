package io.starwars.planet;

import io.starwars.commons.RouterFactoryBase;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

import java.util.Optional;

public class PlanetRouterFactory extends RouterFactoryBase {

  private static final Logger logger = LoggerFactory.getLogger(PlanetRouterFactory.class);

  private PlanetAPIRouting apiRouting;

  @Override
  public void create(Vertx vertx, CircuitBreaker breaker, Handler<AsyncResult<Router>> handlerResult) {
    this.apiRouting = new PlanetAPIRouting(vertx, breaker);
    var config = vertx.getOrCreateContext().config().getJsonObject("api");
    var path = Optional
                  .ofNullable(config.getString("swagger-file-path"))
                  .orElseThrow(() -> new RuntimeException("no such 'swagger-file-path' configuration"));
    logger.debug("Creating router from file {}", path);

    OpenAPI3RouterFactory.create(vertx, path, asyncRouter ->{
      if (asyncRouter.succeeded()) {
        OpenAPI3RouterFactory factory = asyncRouter.result();
        buildRoutes(factory);

        Router router = factory.getRouter();
        enableCorsSupport(router);
        apiRouting.routeFailures(router);
        handlerResult.handle(Future.succeededFuture(router));
      }else{
        logger.error(String.format("Was not possible to create a router with file {} ", path));
        handlerResult.handle(Future.failedFuture(asyncRouter.cause()));
      }
    });
  }

  private void buildRoutes(OpenAPI3RouterFactory factory){
    factory.addHandlerByOperationId("list-planets", apiRouting::listPlanets);
    factory.addHandlerByOperationId("find-by-id", apiRouting::findPlanetById);
    factory.addHandlerByOperationId("find-by-name", apiRouting::findPlanetByName);
    factory.addHandlerByOperationId("create-planet", apiRouting::createPlanet);
    factory.addHandlerByOperationId("delete-planet", apiRouting::deletePlanet);
  }
}
