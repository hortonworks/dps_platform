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
package com.hortonworks.dataplane.gateway.filters;

import com.ecwid.consul.v1.ConsulClient;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;


@Service
public class AuthorizationPreFilter  extends ZuulFilter {

  public static final String DP_PLUGIN_APP = "dp-plugin-app";
  private static Logger log = LoggerFactory.getLogger(AuthorizationPreFilter.class);


  @Override
  public String filterType() {
    return PRE_TYPE;
  }

  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 5;
  }

  @Autowired(required = false)
  public ConsulDiscoveryClient consulClient;


  @Override
  public boolean shouldFilter() {
    RequestContext context = RequestContext.getCurrentContext();
    if(context.get(Constants.USER_CTX_KEY) != null) {
      return false;
    }
    String serviceId = context.get(SERVICE_ID_KEY).toString();

    if ((serviceId.equals(Constants.DPAPP) &&
      context.getRequest().getServletPath().endsWith(Constants.KNOX_CONFIG_PATH)) || context.getRequest().getServletPath().endsWith(Constants.GA_PROPERTIES_PATH)){
      // FIXME: possible security risk
      return false;
    }

    Boolean pluginApp = false;

    if(consulClient!=null) {
      List<ServiceInstance> instances = consulClient.getInstances(serviceId);
      // there is hopefully one instance of this service
      if(instances.get(0)!=null) {
        Map<String, String> metadata = instances.get(0).getMetadata();
        if(metadata.containsKey(DP_PLUGIN_APP)) {
          log.info("Detected "+ serviceId + " as a plugin app");
          pluginApp = true;
        }
      }
    }

    return serviceId.equals(Constants.DPAPP) || serviceId.equals(Constants.DLMAPP) || serviceId.equals(Constants.DSSAPP) || serviceId.equals(Constants.CLOUDBREAK) || pluginApp;
  }

  @Override
  public Object run() {
    throw new GatewayException(HttpStatus.UNAUTHORIZED, "User is not authenticated.");
  }
}
