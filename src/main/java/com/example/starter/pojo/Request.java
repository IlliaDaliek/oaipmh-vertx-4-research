package com.example.starter.pojo;

public class Request {

  private String from;
  private String until;
  private String okapiUrl;
  private String tenant;
  private String okapiToken;
  private boolean suppressFromDiscovery;
  private boolean deletedSupport;

  public String getFrom() {
    return from;
  }

  public Request setFrom(String from) {
    this.from = from;
    return this;
  }

  public String getUntil() {
    return until;
  }

  public Request setUntil(String until) {
    this.until = until;
    return this;
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public Request setOkapiUrl(String okapiUrl) {
    this.okapiUrl = okapiUrl;
    return this;
  }

  public boolean isSuppressFromDiscovery() {
    return suppressFromDiscovery;
  }

  public Request setSuppressFromDiscovery(boolean suppressFromDiscovery) {
    this.suppressFromDiscovery = suppressFromDiscovery;
    return this;
  }

  public boolean isDeletedSupport() {
    return deletedSupport;
  }

  public Request setDeletedSupport(boolean deletedSupport) {
    this.deletedSupport = deletedSupport;
    return this;
  }

  public String getTenant() {
    return tenant;
  }

  public Request setTenant(String tenant) {
    this.tenant = tenant;
    return this;
  }

  public String getOkapiToken() {
    return okapiToken;
  }

  public Request setOkapiToken(String okapiToken) {
    this.okapiToken = okapiToken;
    return this;
  }
}
