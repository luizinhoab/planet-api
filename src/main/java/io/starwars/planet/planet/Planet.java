package io.starwars.planet.planet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "_id", "name", "climate", "terrain", "films-occurrence" })
public class Planet {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;
  private String name;
  private String climate;
  private String terrain;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer occurrenceFilms;
  @JsonProperty("external-url")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String externalUrl;

  public String getId() {
    return id;
  }

  @JsonProperty("_id")
  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getClimate() {
    return climate;
  }

  public void setClimate(String climate) {
    this.climate = climate;
  }

  public String getTerrain() {
    return terrain;
  }

  public void setTerrain(String terrain) {
    this.terrain = terrain;
  }

  @JsonProperty("films-occurrence")
  public Integer getOccurrenceFilms() {
    return occurrenceFilms;
  }

  public void setOccurrenceFilms(Integer occurrenceFilms) {
    this.occurrenceFilms = occurrenceFilms;
  }


  public String getExternalUrl() {
    return externalUrl;
  }

  public void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }
}
