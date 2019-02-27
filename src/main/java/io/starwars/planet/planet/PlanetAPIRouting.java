package io.starwars.planet.planet;

import io.starwars.planet.commons.RoutingBase;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlanetAPIRouting extends RoutingBase {

  private static final Logger logger = LoggerFactory.getLogger(PlanetAPIRouting.class);

  private PlanetMongoRepository repository;
  private SWAPIService service;

  public PlanetAPIRouting(Vertx vertx, CircuitBreaker breaker) {
    this.repository = new PlanetMongoRepository(vertx);
    this.service = new SWAPIService(vertx, breaker);
  }

  public void listPlanets(RoutingContext routingContext){
    var request = routingContext.request();
    logger.debug("{} - {}",request.method(), request.path());
    repository.findAll(event -> {
      if (event.succeeded()) {
        if(event.result().isEmpty()) {
          routingContext.put("message", "No content").fail(204);
        }else {
          var jsonPlanets = event.result();
          var listFuturePlanets = resolveOcurrenceFilms(jsonPlanets);

          CompositeFuture compositeFuture = CompositeFuture.all(listFuturePlanets);
          compositeFuture.setHandler(composition -> {
            if (composition.succeeded()){
              var response = listFuturePlanets.stream().map(f -> f.result()).collect(Collectors.toList());
              sendSuccess(routingContext, Json.encodePrettily(response));
            }else{
              routingContext.fail(composition.cause());
            }
          });
        }
      }else{
        routingContext.fail(event.cause());
      }
    });
  }

  private List<Future> resolveOcurrenceFilms(List<JsonObject> planets){
    return planets.stream().map(jsonObject -> {
      Future future = Future.future();
      wrapperPlanet(jsonObject, asyncPlanet -> {
        if (asyncPlanet.succeeded()){
          future.handle(Future.succeededFuture(asyncPlanet.result()));
        }else{
          future.fail(asyncPlanet.cause());
        }
      });
      return future;
    }).collect(Collectors.toList());
  }

  public void findPlanetByName(RoutingContext routingContext)  {
    var request = routingContext.request();
    logger.debug("{} - {}",request.method(), request.path());
    var name = routingContext.queryParams().get("name");
    if(name ==null || name.isBlank()){
      routingContext.put("message", "Path param 'name' empty.").fail(400);
    }else {
      repository.findByName(name, event -> {
        if (event.succeeded()) {
          if (event.result() == null)
            routingContext.put("message", String.format("Not found with name '%s'",name)).fail(404);
          else {
            wrapperPlanet(event.result(), asyncPlanet ->{
              if (asyncPlanet.succeeded()){
                sendSuccess(routingContext,Json.encodePrettily(asyncPlanet.result()));
              }else{
                routingContext.fail(event.cause());
              }
            });
          }
        } else {
          routingContext.fail(event.cause());
        }
      });
    }
  }

  public void findPlanetById (RoutingContext routingContext) {
    var request = routingContext.request();
    logger.debug("{} - {}",request.method(), request.path());
    var id = routingContext.pathParam("id");

    repository.findById(id, event -> {
      if (event.succeeded()) {
        if (event.result() == null) {
          routingContext.put("message", "Not Found").fail(404);
        } else {
          wrapperPlanet(event.result(), asyncPlanet ->{
            if (asyncPlanet.succeeded()){
              sendSuccess(routingContext,Json.encodePrettily(asyncPlanet.result()));
            }else{
              routingContext.fail(event.cause());
            }
          });
        }
      } else {
        routingContext.fail(event.cause());
      }
    });

  }

  public void createPlanet(RoutingContext routingContext) {
    var request = routingContext.request();
    logger.debug("{} - {}",request.method(), request.path());
    JsonObject body = Optional.ofNullable(routingContext.getBodyAsJson()).orElse(new JsonObject());
    Planet planet = body.mapTo(Planet.class);
    if (planet == null) {
      routingContext.put("message", "Planet could not obtained through of json body.").fail(400);
    }else {
      repository.findByName(planet.getName(), asyncResult -> {
        if (asyncResult.result() != null) {
          routingContext.put("message", "Planet with this name, already exists").fail(409);
        } else {
          service.existsOnSWAPI(planet.getName(), asyncUrl -> {
            if (asyncUrl.succeeded()) {
              if (asyncUrl.result() != null) {
                planet.setExternalUrl(asyncUrl.result());
                repository.create(JsonObject.mapFrom(planet), asyncId -> {
                  if (asyncUrl.succeeded()) {
                    var response = new JsonObject().put("id", asyncId.result());
                    routingContext.response().setStatusCode(201).end(response.toBuffer());
                  } else {
                    routingContext.fail(asyncId.cause());
                  }
                });
              } else {
                routingContext.put("message", "Unknown planet").fail(422);
              }
            } else {
              routingContext.fail(asyncUrl.cause());
            }
          });
        }
      });
    }
  }

  public void deletePlanet(RoutingContext routingContext){
    var request = routingContext.request();
    logger.debug("{} - {}",request.method(), request.path());
    var id = routingContext.pathParam("id");
    if(id != null || !id.isBlank()) {
      repository.delete(id, event -> {
        if (event.succeeded()) {
          if (event.result()) {
            routingContext.response().setStatusCode(202).end();
          } else {
            routingContext.put("message", "planet not found").fail(404);
          }
        } else {
          routingContext.fail(event.cause());
        }
      });
    }else{
      routingContext.put("message", "Path param 'id' not found.").fail(400);
    }
  }

  private void wrapperPlanet(JsonObject json, Handler<AsyncResult<Planet>> resultHandler){
    var url = json.getString("external-url");
     var planet = Json.decodeValue(json.toString(), Planet.class);

    Optional.ofNullable(url).ifPresentOrElse( u ->{
      if (!u.isEmpty()){
        planet.setExternalUrl(u);
        service.countFilmOcurrences(planet.getExternalUrl(), event -> {
          if(event.succeeded()){
            var countFilms = event.result();
            planet.setOccurrenceFilms(countFilms);
            resultHandler.handle(Future.succeededFuture(planet));
          }else{
            resultHandler.handle(Future.failedFuture(event.cause()));
          }
        });
      }else {
        resultHandler.handle(Future.succeededFuture(planet));
      }
    }, () -> resultHandler.handle(Future.succeededFuture(planet))) ;
  }

}

