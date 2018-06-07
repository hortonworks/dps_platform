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
import com.hortonworks.dataplane.gateway.permissions.PermPoliciesService;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.zuul.filters.Route;
import org.springframework.cloud.netflix.zuul.filters.RouteLocator;
import org.springframework.stereotype.Service;

import javax.servlet.ServletInputStream;

import java.io.IOException;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SERVICE_ID_KEY;

@Service
public class PermissionsPolicyRegistrationFilter extends ZuulFilter {
  @Autowired
  private RouteLocator routeLocator;

  @Value("${policy.registration.enabled}")
  private Boolean policyRegistrationEnabled;


  @Autowired
  private PermPoliciesService permPoliciesService;

  @Override
  public String filterType() {
    return PRE_TYPE;
  }

  @Override
  public int filterOrder() {
    return 0;
  }

  @Override
  public boolean shouldFilter() {
    if (!policyRegistrationEnabled) {
      return false;
    }
    RequestContext ctx = RequestContext.getCurrentContext();
    if (ctx.getRequest().getServletPath().endsWith(Constants.PERMS_POLICY_ENTRY_POINT)) {
      for (Route r : routeLocator.getRoutes()) {
        if (ctx.getRequest().getServletPath().equals(r.getPath() + Constants.PERMS_POLICY_ENTRY_POINT)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public Object run() {
    System.out.println("permission policy controller called");
    RequestContext ctx = RequestContext.getCurrentContext();
    try {
      ServletInputStream inputStream = ctx.getRequest().getInputStream();
      String serviceId = ctx.get(SERVICE_ID_KEY).toString();
      permPoliciesService.registerPolicy(serviceId, inputStream);
    } catch (IOException e) {
      ctx.setResponseStatusCode(500);
      ctx.setSendZuulResponse(false);
      throw new RuntimeException("Error reading request");
    }
    return null;
  }
}
