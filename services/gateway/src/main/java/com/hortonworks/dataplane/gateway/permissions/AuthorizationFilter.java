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
package com.hortonworks.dataplane.gateway.permissions;

import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;

@Service
public class AuthorizationFilter extends ZuulFilter {
  private static final Logger logger = LoggerFactory.getLogger(AuthorizationFilter.class);

  @Autowired
  private PermPoliciesService permPoliciesService;

  @Autowired
  private RouteLocator routeLocator;

  @Override
  public String filterType() {
    return PRE_TYPE;
  }

  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 10;
  }

  @Override
  public boolean shouldFilter() {
    RequestContext ctx = RequestContext.getCurrentContext();
    String serviceId = getServiceId();
    if (ctx.get(Constants.ABORT_FILTER_CHAIN)!=null && Boolean.TRUE.equals(ctx.get(Constants.ABORT_FILTER_CHAIN))){
      logger.info("Repsonse already commited. hence ignoring Authorization filter");
      return false;
    }
    return permPoliciesService.hasPolicy(serviceId);
  }

  @Override
  public Object run() {
    RequestContext ctx = RequestContext.getCurrentContext();
    Route matchingRoute = this.routeLocator.getMatchingRoute(ctx.getRequest().getServletPath());
    if (matchingRoute!=null){
      String path=matchingRoute.getPath();
      UserContext userContext = (UserContext) RequestContext.getCurrentContext().get(Constants.USER_CTX_KEY);
      String[] rolesArr = ((userContext == null || userContext.getRoles() == null || userContext.getRoles().isEmpty()) ? new String[]{} : userContext.getRoles().toArray(new String[0]));
      List<String> enabledServices = (userContext == null || userContext.getServices() == null || userContext.getServices().isEmpty()) ? new ArrayList<>() : userContext.getServices();
      boolean isAuthorized = permPoliciesService.isAuthorized(getServiceId(),ctx.getRequest().getMethod(),path,rolesArr );
      String serviceId = getServiceId();
      boolean isEnabled = serviceId.equals(Constants.DPAPP) || enabledServices.indexOf(getServiceId())> -1;
      if (isAuthorized && isEnabled) {
        return null;
      } else if(!isAuthorized) {
        try {
          ctx.getResponse().setStatus(HttpStatus.FORBIDDEN.value());
          ctx.getResponse().sendRedirect(Constants.FORBIDDEN_REDIRECT_URL);
         } catch (IOException e) {
          throw new GatewayException(HttpStatus.FORBIDDEN, "User is not authorized to access this resource.");
        }
        return  null;
      } else {
        try {
          ctx.getResponse().setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
          ctx.getResponse().sendRedirect(Constants.SERVICE_NOT_ENABLED_REDIRECT_URL);
        } catch (IOException e) {
          throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "Service is not enabled");
        }
        return null;
      }
    }else{
      logger.error(String.format("no matching route found for [$s]",ctx.getRequest().getServletPath()));
      return  null;//currently proceeding
    }
  }

  private String getServiceId() {
    RequestContext ctx = RequestContext.getCurrentContext();
    return ctx.get(SERVICE_ID_KEY).toString();
  }
}
