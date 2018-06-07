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

import com.ecwid.consul.v1.ConsistencyMode;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.agent.model.NewCheck;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.agent.model.Service;
import com.ecwid.consul.v1.event.model.Event;
import com.ecwid.consul.v1.event.model.EventParams;
import com.ecwid.consul.v1.health.model.HealthService;
import com.hortonworks.datapalane.consul.model.ConsulEvent;
import com.hortonworks.datapalane.consul.model.ConsulEventResp;

import java.util.List;
import java.util.Map;

public class DpConsulClientImpl implements DpConsulClient {

  private final ConsulClient consulClient;

  public DpConsulClientImpl(ConsulEndpoint consul) {
    consulClient = new ConsulClient(consul.getHost(), consul.getPort());
  }

  @Override
  public ConsulResponse registerService(DpService service) {
    // Add the service and register a check
    NewService newService = new NewService();
    newService.setId(service.getServiceId());
    newService.setTags(service.getServiceTags());
    newService.setPort(service.getPort());
    newService.setName(service.getServiceName());
    newService.setAddress(service.getHost());
    Response<Void> voidResponse = consulClient.agentServiceRegister(newService);
    return ConsulResponse.from(voidResponse);
  }

  @Override
  public ConsulResponse registerCheck(DpService service) {
    NewCheck newCheck = new NewCheck();
    newCheck.setId(service.getServiceId() + "-healthCheck");
    newCheck.setServiceId(service.getServiceId());
    newCheck.setName(service.getServiceId() + "-healthCheck");
    newCheck.setHttp(String.format("http://%s:%s/health",service.getHost(),service.getPort()));
    newCheck.setInterval(service.getHealthCheckIntervalInSecs() + "s");
    newCheck.setDeregisterCriticalServiceAfter(service.getDeregisterServiceAfterInMinutes() + "m");
    newCheck.setTimeout("10s");
    Response<Void> voidResponse = consulClient.agentCheckRegister(newCheck);
    return ConsulResponse.from(voidResponse);
  }


  @Override
  public ConsulResponse unRegisterService(String serviceId) {
    Response<Void> voidResponse = consulClient.agentServiceDeregister(serviceId);
    return ConsulResponse.from(voidResponse);
  }

  @Override
  public ConsulResponse unRegisterCheck(String serviceId) {
    Response<Void> voidResponse = consulClient.agentCheckDeregister(serviceId + "-healthCheck");
    return ConsulResponse.from(voidResponse);
  }

  @Override
  public ConsulResponse<List<HealthService>> getService() {
    Response<List<HealthService>> healthServices = consulClient.getHealthServices("zuul", true, QueryParams.DEFAULT);
    return ConsulResponse.from(healthServices);
  }

  @Override
  public boolean checkServiceAvailability(String serviceId) {
    Response<Map<String, Service>> agentServices = consulClient.getAgentServices();
    return agentServices.getValue().keySet().contains(serviceId);
  }

  @Override
  public ConsulEventResp fireEvent(ConsulEvent event){
    EventParams eventParams = new EventParams();
    QueryParams queryParams=new QueryParams(ConsistencyMode.CONSISTENT);
    if (event.getServiceName()!=null) {
      eventParams.setService(event.getServiceName());
    }
    Response<Event> resp=consulClient.eventFire(event.getName(),event.getPayload(),eventParams,queryParams);
    ConsulEventResp eventResp=new ConsulEventResp(resp.getValue().getId(),resp.getValue().getlTime());
    return eventResp;
  }
}
