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

import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.service.PluginManifestService;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

@Service
public class AuthorizationPreFilter extends ZuulFilter {
  private static final Logger logger = LoggerFactory.getLogger(AuthorizationPreFilter.class);

  @Autowired
  private PluginManifestService pluginService;

  @Autowired
  private Config config;

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
    String serviceId = ctx.get(SERVICE_ID_KEY).toString();

    if (ctx.get(Constants.ABORT_FILTER_CHAIN) != null && Boolean.TRUE.equals(ctx.get(Constants.ABORT_FILTER_CHAIN))) {
      logger.info("Repsonse already commited. Hence ignoring Authorization filter");
      return false;
    }

    // allow whitelisted services
    if(config.getStringList("dp.gateway.whitelisted_services").contains(serviceId)) {
      return false;
    }

    return true;
  }


  @Override
  public Object run() {
    RequestContext ctx = RequestContext.getCurrentContext();

    String serviceId = ctx.get(SERVICE_ID_KEY).toString();
    UserContext userContext = (UserContext) ctx.get(Constants.USER_CTX_KEY);
    String[] rolesArr = ((userContext == null || userContext.getRoles() == null || userContext.getRoles().isEmpty()) ? new String[]{} : userContext.getRoles().toArray(new String[0]));

    boolean isAuthorized = pluginService.isAuthorized(serviceId, rolesArr);

    if (isAuthorized) {
      return null;
    } else {
      try {
        ctx.getResponse().setStatus(HttpStatus.FORBIDDEN.value());
        ctx.getResponse().sendRedirect(Constants.FORBIDDEN_REDIRECT_URL);
      } catch (IOException e) {
        throw new GatewayException(HttpStatus.FORBIDDEN, "User is not authorized to access this resource.");
      }
      return null;
    }
  }
}
