package io.starwars.planet.commons;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import java.util.Optional;

public class MongoClientFactory {
  private static final Logger logger = LoggerFactory.getLogger(MongoClientFactory.class);

  private JsonObject config;

  public MongoClient createShared(Vertx vertx, String poolName) {
    logger.info("Creating Mongo Client with shared pool");
    configurateMongoClient();
    return MongoClient.createShared(vertx, config, poolName);
  }

  public MongoClient createNonShared(Vertx vertx) {
    logger.info("Creating Mongo Client with NOT shared pool");
    configurateMongoClient();
    return MongoClient.createNonShared(vertx, config);
  }

  private void configurateMongoClient(){
    this.config = Vertx.currentContext().config();
    this.config = Optional
      .ofNullable(config.getJsonObject("mongo"))
      .orElseThrow(() -> new RuntimeException("no such 'mongo' configuration"));
  }

}
