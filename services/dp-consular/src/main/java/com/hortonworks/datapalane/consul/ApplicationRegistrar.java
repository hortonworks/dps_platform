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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ApplicationRegistrar {

  private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
  private Config config;
  private Optional<ConsulHook> hook;
  private InetUtils inetUtils=new InetUtils(new InetUtilsProperties());

  private String DEREGISTER_CRITICAL_SERIVE_TTL_PROPNAME="consul.service.deregister.afterMinutes";
  public ApplicationRegistrar(Config config, Optional<ConsulHook> hook) {
    this.config = config;
    this.hook = hook;
  }

  public void initialize() {
    if(!checkEnabled())
      return;
    String consulHost = config.getString("consul.host");
    int consulPort = config.getInt("consul.port");
    DpConsulClientImpl dpConsulClient = new DpConsulClientImpl(new ConsulEndpoint(consulHost, consulPort));

    String serviceName = config.getString("consul.serviceName");
    List<String> serviceTags = config.getStringList("consul.service.tags");
    int servicePort = config.getInt("consul.service.port");
    String serviceId = generateServiceId(serviceName,servicePort);
    DpService dpService = new DpService(serviceId, serviceName, serviceTags, getServiceAddress(),servicePort);

    if (config.hasPath(DEREGISTER_CRITICAL_SERIVE_TTL_PROPNAME) && !config.getIsNull(DEREGISTER_CRITICAL_SERIVE_TTL_PROPNAME)) {
      int deregisterServiceAfter = config.getInt(DEREGISTER_CRITICAL_SERIVE_TTL_PROPNAME);
      dpService.setDeregisterServiceAfterInMinutes(deregisterServiceAfter);
    }
    ClientStart clientStartTask = new ClientStart(dpConsulClient, dpService, hook);
    ClientStatus clientStatusTask = new ClientStatus(dpConsulClient, dpService, hook);
    ExecutionHandler executionHandler = new ExecutionHandler(scheduledExecutorService, () -> clientStartTask, () -> clientStatusTask, config, hook);
    clientStartTask.setExecutionHandler(Optional.of(executionHandler));
    clientStatusTask.setExecutionHandler(Optional.of(executionHandler));
    scheduledExecutorService.schedule(clientStartTask, 2, TimeUnit.SECONDS);
    Runtime.getRuntime().addShutdownHook(new ShutdownHook(dpConsulClient, dpService, hook,scheduledExecutorService));
  }

  private Boolean checkEnabled() {
    Boolean enabled;
    try {
      enabled = config.getBoolean("consul.enabled");
    } catch (ConfigException ce){
      enabled = true;
    }
    return enabled;
  }

  private String getServiceAddress() {
    return inetUtils.findFirstNonLoopbackAddress().getHostAddress();
  }
  private String generateServiceId(String serviceName,int servicePort){
    return String.format("%s_%s:%d",serviceName,this.getServiceAddress(),servicePort);
  }

  private static class ExecutionHandler {

    private final ScheduledExecutorService executorService;
    private final Supplier<ClientStart> clientStartSupplier;
    private final Supplier<ClientStatus> clientStatusSupplier;
    private final Config config;
    private final Optional<ConsulHook> hook;

    public ExecutionHandler(ScheduledExecutorService executorService, Supplier<ClientStart> clientStartSupplier,
                            Supplier<ClientStatus> clientStatusSupplier, Config config, Optional<ConsulHook> hook) {
      this.executorService = executorService;
      this.clientStartSupplier = clientStartSupplier;
      this.clientStatusSupplier = clientStatusSupplier;
      this.config = config;
      this.hook = hook;
    }

    void onFailure() {
      int retryInterval = getRetryInterval();
      executorService.schedule(clientStartSupplier.get(), retryInterval, TimeUnit.SECONDS);
    }

    private int getRetryInterval() {
      int retryInterval = 5;
      try {
        retryInterval = config.getInt("consul.client.connect.failure.retry.secs");
      } catch (Throwable th) {
        if (hook.isPresent()) {
          hook.get().onRecoverableException("Recovered from a config load exception (consul.client.connect.failure.retry.secs) with a default value " + retryInterval, th);
        }
      }
      return retryInterval;
    }

    void onSuccess() {
      int retryInterval = getRetryInterval();
      // registration was successful
      // schedule a consul health check which
      // checks if the service is already registered in consul
      // if yes then repeat the check after a predefined interval
      // if the check fails - consul down/otherwise, start the
      // registration loop all over again
      executorService.schedule(clientStatusSupplier.get(), retryInterval, TimeUnit.SECONDS);
    }
  }


  private static class ClientStart implements Runnable {

    private final DpConsulClient dpConsulClient;
    private Optional<ExecutionHandler> executionHandler = Optional.empty();
    private final DpService dpService;
    private final Optional<ConsulHook> consulHook;

    public void setExecutionHandler(Optional<ExecutionHandler> executionHandler) {
      this.executionHandler = executionHandler;
    }

    public ClientStart(DpConsulClient dpConsulClient, DpService dpService, Optional<ConsulHook> consulHook) {
      this.dpConsulClient = dpConsulClient;
      this.dpService = dpService;
      this.consulHook = consulHook;
    }

    @Override
    public void run() {
      try {
        dpConsulClient.registerService(dpService);
        consulHook.ifPresent(consulHook -> consulHook.onServiceRegistration(dpService));
        dpConsulClient.registerCheck(dpService);
        executionHandler.ifPresent(ExecutionHandler::onSuccess);
        // if at any point in time consul is
      } catch (Throwable th) {
        consulHook.ifPresent(consulHook -> consulHook.serviceRegistrationFailure(dpService.getServiceId(), th));
        executionHandler.ifPresent(ExecutionHandler::onFailure);
      }
    }
  }


  private static class ClientStatus implements Runnable {

    private final DpConsulClient dpConsulClient;
    private Optional<ExecutionHandler> executionHandler = Optional.empty();
    private final DpService dpService;
    private final Optional<ConsulHook> consulHook;

    public void setExecutionHandler(Optional<ExecutionHandler> executionHandler) {
      this.executionHandler = executionHandler;
    }

    public ClientStatus(DpConsulClient dpConsulClient, DpService dpService, Optional<ConsulHook> consulHook) {
      this.dpConsulClient = dpConsulClient;
      this.dpService = dpService;
      this.consulHook = consulHook;
    }

    @Override
    public void run() {
      try {
        String serviceId = dpService.getServiceId();
        boolean available = dpConsulClient.checkServiceAvailability(serviceId);
        consulHook.ifPresent(consulHook -> consulHook.onServiceCheck(serviceId));
        if (available)
          executionHandler.ifPresent(ExecutionHandler::onSuccess);
        else
          executionHandler.ifPresent(ExecutionHandler::onFailure);
        // if at any point in time consul is
      } catch (Throwable th) {
        consulHook.ifPresent(consulHook -> consulHook.onRecoverableException("Service availability could not be confirmed, " +
          "Fault was recovered and we will attempt a reregistration", th));
        executionHandler.ifPresent(ExecutionHandler::onFailure);
      }
    }
  }


  private static class ShutdownHook extends Thread {


    private final DpConsulClient dpConsulClient;
    private final DpService dpService;
    private final Optional<ConsulHook> consulHook;
    private final ScheduledExecutorService scheduledExecutorService;

    public ShutdownHook(DpConsulClient dpConsulClient, DpService dpService, Optional<ConsulHook> consulHook, ScheduledExecutorService scheduledExecutorService) {
      this.dpConsulClient = dpConsulClient;
      this.dpService = dpService;
      this.consulHook = consulHook;
      this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public void run() {
      try {
        String serviceId = dpService.getServiceId();
        dpConsulClient.unRegisterService(serviceId);
        dpConsulClient.unRegisterCheck(serviceId);
        scheduledExecutorService.shutdown();
        consulHook.ifPresent(consulHook -> consulHook.onServiceDeRegister(serviceId));
      } catch (Throwable th) {
        consulHook.ifPresent(consulHook -> consulHook.onRecoverableException("ShutDown hook failed", th));
      }
    }
  }

}
