package com.example.starter.pojo;

import java.util.UUID;

public class Instance {

  private UUID id;
  private String jsonb;
  private UUID requestId;

  public Instance() {
  }

  public Instance(UUID id, String jsonb, UUID requestId) {
    this.id = id;
    this.jsonb = jsonb;
    this.requestId = requestId;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getJsonb() {
    return jsonb;
  }

  public void setJsonb(String jsonb) {
    this.jsonb = jsonb;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public void setRequestId(UUID requestId) {
    this.requestId = requestId;
  }

  public Instance withId(UUID id) {
    this.id = id;
    return this;
  }

  public Instance withJsonb(String jsonb) {
    this.jsonb = jsonb;
    return this;
  }

  public Instance withRequestId(UUID requestId) {
    this.requestId = requestId;
    return this;
  }

}
