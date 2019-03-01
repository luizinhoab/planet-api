package io.starwars.commons;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface Repository {

  void findAll(Handler<AsyncResult<List<JsonObject>>> handleResult);
  void findById(String id, Handler<AsyncResult<JsonObject>> handleResult);
  void findByName(String name, Handler<AsyncResult<JsonObject>> handleResult);
  void create(JsonObject planet, Handler<AsyncResult<String>> handleResult);
  void delete(String id, Handler<AsyncResult<Boolean>> handleResult);

}

