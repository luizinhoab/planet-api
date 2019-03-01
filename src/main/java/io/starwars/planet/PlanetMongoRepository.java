package io.starwars.planet;

import io.starwars.commons.MongoClientFactory;
import io.starwars.commons.Repository;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

public class PlanetMongoRepository implements Repository {

  private static final Logger logger = LoggerFactory.getLogger(PlanetMongoRepository.class);
  private static final String COLLECTION = "planets";
  private MongoClient client;

  public PlanetMongoRepository(Vertx vertx) {
    this.client = new MongoClientFactory().createShared(vertx, "planet-pool");
  }

  @Override
  public void findAll(Handler<AsyncResult<List<JsonObject>>> handleResult) {
    logger.debug("Retrieve all planets");
    client.find(COLLECTION, new JsonObject() ,handleResult);
  }

  @Override
  public void findById(String id, Handler<AsyncResult<JsonObject>> handleResult) {
    logger.debug("Retrieve planet by id {}", id);
    client.findOne(COLLECTION, new JsonObject().put("_id", id), null,handleResult);
  }

  @Override
  public void findByName(String name, Handler<AsyncResult<JsonObject>> handleResult) {
    logger.debug("Retrieve planet by name {}", name);
    client.findOne(COLLECTION, new JsonObject().put("name", name), null, handleResult);
  }

  @Override
  public void create(JsonObject planet, Handler<AsyncResult<String>> handleResult) {
    logger.debug("Creating planet \n", planet.encodePrettily());
    client.insert(COLLECTION, planet, handleResult);
  }

  @Override
  public void delete(String id, Handler<AsyncResult<Boolean>> handleResult) {
    client.removeDocument(COLLECTION, new JsonObject().put("_id", id), event -> {
      if (event.succeeded()) {
        var removedCount = event.result().getRemovedCount();
        if(removedCount>0){
          logger.debug("The planet id {} was deleted", id);
          handleResult.handle(Future.succeededFuture(true));
        }else{
          logger.warn("The planet id {} not exists.");
          handleResult.handle(Future.succeededFuture(false));
        }
      }else{
        logger.error("Was not possible delete planet id {}", event.cause(), id);
        handleResult.handle(Future.failedFuture(event.cause()));
      }
    });
  }
}
