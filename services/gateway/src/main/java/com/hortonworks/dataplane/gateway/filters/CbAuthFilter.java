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

import com.google.common.net.InternetDomainName;
import com.hortonworks.dataplane.gateway.domain.CloudbreakContext;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.utils.Jwt;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static com.hortonworks.dataplane.gateway.domain.Constants.CLOUDBREAK;
import static com.hortonworks.dataplane.gateway.utils.Conditions.*;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

//TODO: Ashwin - Add cloudbreak Headers before forward and handle redirections if any

@Service
public class CbAuthFilter extends ZuulFilter {

  public static final String DEFAULT_TLD = ".com";
  public static final String API_PATH = "/service/cloudbreak/cloudbreak";
  public static final String STATIC_ASSETS_SSI_BASE_HREF_TMPL_HTML = "/service/cloudbreak/static/assets/ssi/base-href.tmpl.html";
  private static Logger log = LoggerFactory.getLogger(CbAuthFilter.class);

  @Override
  public String filterType() {
    return PRE_TYPE;
  }

  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 10;
  }

  @Autowired
  private Config config;

  @Autowired
  private Jwt jwt;

  @Override
  public boolean shouldFilter() {
    RequestContext context = RequestContext.getCurrentContext();
    String serviceId = context.get(SERVICE_ID_KEY).toString();
    return serviceId.equals(CLOUDBREAK);
  }

  @Override
  public Object run() {
    RequestContext context = RequestContext.getCurrentContext();
    if (context.getRequest().getRequestURI().equals(STATIC_ASSETS_SSI_BASE_HREF_TMPL_HTML)) {
      // write the required template in the response
      context.setResponseBody("<base href='/cloudbreak/'>");
    } else {
      UserContext userContext = (UserContext) context.get(Constants.USER_CTX_KEY);
      // Get the forwarded header
      Optional<String> host = Optional.ofNullable(context.getRequest().getHeader("X-Forwarded-Host"));
      ifNull(userContext).throwError(new GatewayException(HttpStatus.FORBIDDEN, "User does not have access to " + CLOUDBREAK));
      ifFalse(host.isPresent()).throwError(new GatewayException(HttpStatus.INTERNAL_SERVER_ERROR, "No host header to be forwarded"));
      CloudbreakContext cloudbreakContext = CloudbreakContext.from(userContext);
      // Create an email from domain and username
      String email = cloudbreakContext.getUsername() + "@" + host.get();
      cloudbreakContext.setSecret(config.getString("dp.gateway.services.cloudbreak.key"));
      cloudbreakContext.setEmail(email);
      cloudbreakContext.setDomain(host.get());
      String jwt = this.jwt.makeJWT(cloudbreakContext);
      log.info("Sending cloudbreak request to " + context.getRequest().getRequestURI());
      if (!context.getRequest().getRequestURI().startsWith(API_PATH)) {
        context.addZuulRequestHeader(Constants.AUTHORIZATION_HEADER, "Bearer " + jwt);
      }
    }
    return null;
  }


}
