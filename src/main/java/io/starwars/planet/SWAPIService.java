package io.starwars.planet;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.http.HttpStatus;

import java.util.Optional;

public class SWAPIService{

  private static final Logger logger = LoggerFactory.getLogger(SWAPIService.class);

  private Vertx vertx;
  private CircuitBreaker breaker;
  private WebClient webClient;
  private WebClientOptions options;

  public SWAPIService(Vertx vertx, CircuitBreaker breaker) {
    this.vertx = vertx;
    var config=vertx.getOrCreateContext().config().getJsonObject("swapi");
    var host = config.getString("host","swapi.co");
    var port = config.getInteger("port", 443);

    this.options = new WebClientOptions()
                        .setSsl(true)
                        .setTrustAll(true)
                        .setDefaultHost(host)
                        .setDefaultPort(port)
                        .setLogActivity(true);

    this.breaker = breaker;


  }

  public void getPlanetByName(String name, Handler<AsyncResult<JsonObject>> resultHandler){
    var config=vertx.getOrCreateContext().config().getJsonObject("swapi");
    var resource = config.getString("base-resource","");
    this.webClient = WebClient.create(vertx, options);


    breaker.<HttpResponse<Buffer>>execute(future -> {
       webClient.get(resource)
        .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
        .addQueryParam("search",name)
        .send(event -> {
          if (event.succeeded()) {
            var response = event.result();
            if (response.statusCode() == HttpStatus.SC_OK) {
              var body = response.bodyAsJsonObject();
              if (body.getInteger("count")==1) {
                var bodyItem = body.getJsonArray("results").getJsonObject(0);
                logger.debug("get - {}{} - status: {} \n", options.getDefaultHost(), resource, response.statusCode(), body);
                resultHandler.handle(Future.succeededFuture(bodyItem));
              }else{
                logger.warn("get - {}{} - status: {} \n", options.getDefaultHost(), resource, response.statusCode(), body);
                resultHandler.handle(Future.succeededFuture());
              }
            }else{
              logger.warn("get - {}{} - status: {}", options.getDefaultHost(), resource, response.statusCode());

              resultHandler.handle(Future.succeededFuture());
            }
          }else{
            logger.error("get - {}{}", event.cause(), options.getDefaultHost(), resource);
            resultHandler.handle(Future.failedFuture(event.cause()));
          }
          future.complete();
        });
    });
  }
  public void countFilmOcurrences(String resourceUrl, Handler<AsyncResult<Integer>> resultHandler){
    var webClient = WebClient.create(vertx,  options);
    var config=vertx.getOrCreateContext().config().getJsonObject("swapi");
    var resource = config.getString("base-resource","").concat("/");
    var id = resourceUrl.charAt(resourceUrl.length()-2);

    webClient
      .get(resource.concat(String.valueOf(id)).concat("?format=json"))
      .send(event -> {
        if(event.succeeded()){
          var response = event.result();
          if (response.statusCode() == HttpStatus.SC_OK) {
            Optional.ofNullable(response.bodyAsJsonObject())
              .ifPresentOrElse(body -> {
                logger.debug("get - {}{} - status: {} \n", options.getDefaultHost(), resource, response.statusCode(), body);
                var films = body.getJsonArray("films");
                resultHandler.handle(Future.succeededFuture(films.size()));
              }, () -> resultHandler.handle(Future.succeededFuture()));
          }else{
            logger.warn("get - {}{} - status: {} \n", options.getDefaultHost(), resource, response.statusCode());
            resultHandler.handle(Future.failedFuture(new IllegalStateException("Was not possible obtain the count ocurrences of films")));
          }
        }else{
          logger.error("get - {}{}", event.cause(), resourceUrl);
          resultHandler.handle(Future.failedFuture(event.cause()));
        }
      });
      }

  public void existsOnSWAPI(String name, Handler<AsyncResult<String>> resultHandler) {
    logger.debug("Verify if planet exists, by name '{}' on swapi.com", name);
    getPlanetByName(name, async -> {
      if (async.succeeded()) {
        Optional.ofNullable(async.result()).ifPresentOrElse(jsonPlanet -> {
          var foundName = jsonPlanet.getString("name");
          if (name.equalsIgnoreCase(foundName)) {
            var externalUrl = jsonPlanet.getString("url");
            externalUrl=externalUrl
              .replace("http://", "")
              .replace("https://", "");
            resultHandler.handle(Future.succeededFuture(externalUrl));
          } else {
            resultHandler.handle(Future.succeededFuture());
          }
        }, () -> resultHandler.handle(Future.succeededFuture()));
      } else {
        logger.error("Was not possible verify on swapi");
        resultHandler.handle(Future.failedFuture(async.cause()));
      }
    });
  }

}
