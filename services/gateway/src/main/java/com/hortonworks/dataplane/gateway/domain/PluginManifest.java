package com.hortonworks.dataplane.gateway.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginManifest {
  @JsonProperty
  Long id;
  @JsonProperty
  String prefix;
  @JsonProperty
  String name;
  @JsonProperty
  String label;
  @JsonProperty("require_platform_roles")
  List<String> requirePlatformRoles;

  public Long getId() {
    return id;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getName() {
    return name;
  }

  public String getLabel() {
    return label;
  }

  public List<String> getRequirePlatformRoles() {
    return requirePlatformRoles;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public void setRequirePlatformRoles(List<String> requirePlatformRoles) {
    this.requirePlatformRoles = requirePlatformRoles;
  }

  @Override
  public String toString() {
    return "PluginManifest{" +
      "id=" + id +
      ", prefix='" + prefix + '\'' +
      ", name='" + name + '\'' +
      ", label='" + label + '\'' +
      ", requirePlatformRoles=" + requirePlatformRoles +
      '}';
  }
}
