/*
 *
 *  *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *  *
 *  *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *  *
 *  *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *  *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *  *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *  *   properly licensed third party, you do not have any rights to this code.
 *  *
 *  *   If this code is provided to you under the terms of the AGPLv3:
 *  *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *  *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *  *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *  *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *  *     FROM OR RELATED TO THE CODE; AND
 *  *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *  *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *  *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *  *     OR LOSS OR CORRUPTION OF DATA.
 *
 */
package com.hortonworks.dataplane.gateway.monitor;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.*;
import com.hortonworks.dataplane.gateway.service.MetricInterface;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@RestController
@RequestMapping("/service")
public class ServiceMetricsController {

  private static final Map<String,String> METERED_SERVICES = ImmutableMap.of("db","db-service", "clusters","cluster-service", "core", "core","dlm","dlm", "dss", "dss");
  private static Logger log = LoggerFactory.getLogger(ServiceMetricsController.class);


  @Autowired
  DiscoveryClient client;


  private MetricInterface buildInterface(URL url) {
    return Feign.builder().decoder(new JacksonDecoder()).target(MetricInterface.class, url.toString());
  }


  @RequestMapping("/metrics")
  public Object run() {
    Table<String, String, String> table = HashBasedTable.create();
    List<String> services = client.getServices();
    Stream<Service> listStream = services.stream().filter(s -> METERED_SERVICES.keySet().contains(s)).map(s -> {

      List<org.springframework.cloud.client.ServiceInstance> instances = client.getInstances(s);
      List<ServiceInstance> urlList = instances.stream().map(serviceInstance -> {
        try {
          String protocol = serviceInstance.isSecure() ? "https" : "http";
          return Optional.of(new ServiceInstance(serviceInstance.getHost() + ":" + serviceInstance.getPort(), new URL(protocol + "://" + serviceInstance.getHost() + ":" + serviceInstance.getPort())));
        } catch (MalformedURLException e) {
          log.error("Service URL was malformed", e);
        }
        return Optional.empty();
      }).filter(Optional::isPresent).map(u -> ((Optional<ServiceInstance>) u).get()).collect(Collectors.toList());

      return new Service(s, urlList);

    });


    listStream.forEach(service -> {
      service.getServiceInstance().forEach(instance -> {
        table.put(METERED_SERVICES.get(service.getName()), instance.getName(), instance.getService().toString());
      });
    });


    //Finally get info from each service
    HashSet<ServiceEndpoint> sepSet = Sets.newHashSet();
    table.cellSet().forEach(cell -> {
      try {
        ServiceEndpoint sep = new ServiceEndpoint(cell.getRowKey(),cell.getColumnKey(),cell.getValue()+"/metrics");
        sepSet.add(sep);
      } catch (Throwable th) {
        log.error("Cannot get endpoint info for service " + cell.getRowKey() + ":" + cell.getColumnKey());
      }
    });

    return sepSet;
  }

  private static class Service {
    private String name;
    private List<ServiceInstance> serviceInstance;

    public Service(String name, List<ServiceInstance> serviceInstance) {
      this.name = name;
      this.serviceInstance = serviceInstance;
    }

    public String getName() {
      return name;
    }

    public List<ServiceInstance> getServiceInstance() {
      return serviceInstance;
    }
  }


  private static class ServiceInstance {
    private String name;
    private URL services;

    public ServiceInstance(String name, URL services) {
      this.name = name;
      this.services = services;
    }

    public String getName() {
      return name;
    }

    public URL getService() {
      return services;
    }
  }

  private class ServiceEndpoint{
    private String service;
    private String instance;

    @JsonProperty("metrics_url")
    private String metricsUrl;

    public String getMetricsUrl() {
      return metricsUrl;
    }

    public void setMetricsUrl(String metricsUrl) {
      this.metricsUrl = metricsUrl;
    }

    public String getService() {
      return service;
    }

    public void setService(String service) {
      this.service = service;
    }

    public String getInstance() {
      return instance;
    }

    public void setInstance(String instance) {
      this.instance = instance;
    }

    public ServiceEndpoint(String service, String instance, String metricsUrl){
      this.service = service;
      this.instance = instance;
      this.metricsUrl = metricsUrl;
    }

  }

}
