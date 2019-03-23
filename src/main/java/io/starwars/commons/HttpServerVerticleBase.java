package io.starwars.commons;

import io.vertx.core.*;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;

import java.util.Optional;

public class HttpServerVerticleBase extends MicroServicesVerticleBase {

  private static Logger logger = LoggerFactory.getLogger(HttpServerVerticleBase.class);

  protected String host;
  protected int port;
  protected Router router;

  @Override
  public void init(Vertx vertx, Context context) {

    super.init(vertx, context);

    var serverConfig = Optional.ofNullable(config().getJsonObject("server")).orElse(new JsonObject());
    this.port = serverConfig.getInteger("port", 8080);
    this.host = serverConfig.getString("host", "localhost");


  }

  public void start(Future<Void> startFuture){
    logger.info("Http Server starting ...");
    var options = new HttpServerOptions().setLogActivity(true);
    this.server = vertx.createHttpServer(options);



    this.server.requestHandler(req ->{
        if (req.path().equals("/")) {
          req.response()
            .putHeader("content-type", "text/plain")
            .end("up");
        }else{
          router.get("/health/server").handler(this::serverHealthCheckHandler);
          router.get("/health").handler(this::metricsHealthCheckHandler);
          router.accept(req);
        }
      })
      .listen(this.port, this.host, event ->{
        if(event.succeeded()){
          logger.info("Server initialized listen on {}:{}",this.host , String.valueOf(this.port));
          startFuture.complete();
        }else{
          logger.error("Server initialization not reached \n", event.cause());
          startFuture.fail(event.cause());
        }
      });
  }

  public void setRouter(Router router) {
    this.router = router;
  }
}
