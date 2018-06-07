/*
 *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *   properly licensed third party, you do not have any rights to this code.
 *
 *   If this code is provided to you under the terms of the AGPLv3:
 *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *     FROM OR RELATED TO THE CODE; AND
 *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 */

package com.hortonworks.datapalane.consul;

import com.ecwid.consul.v1.health.model.HealthService;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * Client Gateway component
 * By convention all service URL's
 * should be first picked up from System.properties and then from
 * the underlying configuration
 * <p>
 * Clients should construct a Gateway with
 * the configuration and a map of service endpoints to
 * be written into System properties
 * <p>
 * This component will periodically check for zuul
 * to be available and use of the servers to construct the
 * target url and write it into system properties
 */
public class Gateway {

  public static final int INITIAL_DELAY = 1;
  public static final int DEFAULT_GATEWAY_DISCOVER_RETRY_COUNT = 5;
  public static final int DFAULT_GATEWAY_DISCOVER_RETRY_WAITBETWEEN_INMILLIS = 3000;
  private final Config config;
  private final Map<String, String> serviceConfigs;
  private final Optional<ConsulHook> consulHook;
  private final DpConsulClientImpl dpConsulClient;
  private final AtomicReference<Set<ZuulServer>> serverSet;
  private Supplier<List<ZuulServer>> supplier;
  private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  private int gatewayDiscoverRetryCount=DEFAULT_GATEWAY_DISCOVER_RETRY_COUNT;


  public Gateway(Config config, Map<String, String> serviceConfigs, Optional<ConsulHook> consulHook) {
    this.config = config;
    this.serviceConfigs = serviceConfigs;
    this.consulHook = consulHook;
    String host = config.getString("consul.host");
    int port = config.getInt("consul.port");
    if (config.hasPath("gateway.discover.retry.count")) {
      gatewayDiscoverRetryCount=config.getInt("gateway.discover.retry.count");
    }
    dpConsulClient = new DpConsulClientImpl(new ConsulEndpoint(host, port));
    supplier = new ServerListSupplier(dpConsulClient);
    serverSet = new AtomicReference<>(Sets.newHashSet());
  }

  /**
   * Overwrite the system property
   */
  public void initialize() {
    final Random randomizer = new Random();
    int refresh = 60;
    if (!config.getIsNull("gateway.refresh.servers.secs")) {
      refresh = config.getInt("gateway.refresh.servers.secs");
    }
    Runnable runnable = () -> {
      try {

        // Simple change detection to avoid overwriting properties on every run
        // Memoize every run so we can just check id any new servers were added
        // if server lists were updated, pick one randomly
        List<ZuulServer> zuulServers = supplier.get();
        HashSet<ZuulServer> currentSet = Sets.newHashSet(zuulServers);
        // IF current set is same as old set do not update properties
        if (!serverSet.get().isEmpty() && currentSet.containsAll(serverSet.get())) {
          consulHook.ifPresent(consulHook -> consulHook.onRecoverableException("Server list not updated since there was no change in Consul", new Exception("Server list not updated")));
          return;
        }

        if (zuulServers.size() == 0)
          throw new Exception("No Zuul servers found");
        ZuulServer zuulServer = zuulServers.get(randomizer.nextInt(zuulServers.size()));
        serviceConfigs.forEach((k, v) -> {
          System.setProperty(k, zuulServer.makeUrl(config.getBoolean("gateway.ssl.enabled")) + v);
        });
        // remember the old server list for next run
        serverSet.set(currentSet);
        if (consulHook.isPresent() && zuulServers.size() > 0) {
          consulHook.get().gatewayDiscovered(zuulServer);
        }
      } catch (Throwable th) {
        //Clear server set for rediscovery
        serverSet.get().clear();
        serviceConfigs.forEach((k, v) -> {
          System.clearProperty(k);
        });

        consulHook.ifPresent(consulHook -> consulHook.gatewayDiscoverFailure("Error getting gateway URL, removing service configs from System, fallback will proceed", th));

      }
    };

    scheduledExecutorService.scheduleAtFixedRate(runnable, INITIAL_DELAY, refresh, TimeUnit.SECONDS);

  }

  public void getGatewayService(GatewayHook gatewayHook) {
    final Random randomizer = new Random();
    AtomicInteger retryCounter = new AtomicInteger(gatewayDiscoverRetryCount);
    AtomicReference<Future> futureRef = new AtomicReference<>();
    Future future = scheduledExecutorService.scheduleWithFixedDelay(() -> {
      List<ZuulServer> zuulServers = supplier.get();
      if (zuulServers.size() > 0) {
        try {
          gatewayHook.gatewayDiscovered(zuulServers.get(randomizer.nextInt(zuulServers.size())));
        } finally {
          futureRef.get().cancel(true);
        }
      } else {
        if (retryCounter.decrementAndGet() < 0) {
          try {
            gatewayHook.gatewayDiscoverFailure("Not able to discover gateway after retrying " + gatewayDiscoverRetryCount + " times.");
          } finally {
            futureRef.get().cancel(true);
          }
        }
      }

    }, INITIAL_DELAY, DFAULT_GATEWAY_DISCOVER_RETRY_WAITBETWEEN_INMILLIS, TimeUnit.MILLISECONDS);
    futureRef.set(future);
  }

  private static class ServerListSupplier implements Supplier<List<ZuulServer>> {


    private final DpConsulClient dpConsulClient;

    public ServerListSupplier(DpConsulClient dpConsulClient) {
      this.dpConsulClient = dpConsulClient;
    }

    @Override
    public List<ZuulServer> get() {
      ConsulResponse<List<HealthService>> zuul = dpConsulClient.getService();
      List<HealthService> value = zuul.getUnderlying().getValue();
      return value.stream().map(healthService ->
        new ZuulServer(healthService.getService().getAddress(),
          healthService.getService().getPort(),
          healthService.getNode().getNode()))
        .collect(Collectors.toList());
    }
  }
}
