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

import com.hortonworks.dataplane.gateway.auth.AuthenticationService;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.utils.CookieUtils;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;


@Service
public class TokenAuthPreFilter extends ZuulFilter {

  @Autowired
  private AuthenticationService authenticationService;

  @Autowired
  private CookieUtils cookieUtils;

  @Override
  public String filterType() {
    return PRE_TYPE;
  }

  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 2;
  }

  @Override
  public boolean shouldFilter() {
    RequestContext context = RequestContext.getCurrentContext();
    HttpServletRequest request = context.getRequest();
    String authHeader = request.getHeader(Constants.AUTHORIZATION_HEADER);
    String authCookie = cookieUtils.getDataplaneToken();
    return context.get(Constants.USER_CTX_KEY) == null && ((authHeader != null && authHeader.startsWith(Constants.AUTH_HEADER_PRE_BEARER)) || authCookie != null);
  }

  @Override
  public Object run() {
    RequestContext context = RequestContext.getCurrentContext();
    HttpServletRequest request = context.getRequest();
    String authHeader = request.getHeader(Constants.AUTHORIZATION_HEADER);
    String authCookie = cookieUtils.getDataplaneToken();
    String token = null;

    if(authHeader != null && authHeader.startsWith(Constants.AUTH_HEADER_PRE_BEARER)) {
      token = authHeader.substring(Constants.AUTH_HEADER_PRE_BEARER.length());
    } else if(authCookie != null) {
      token = authCookie;
    }

    if(token == null) {
      throw new GatewayException(HttpStatus.BAD_REQUEST, "Unable to find token.");
    }

    UserContext userContext = authenticationService.buildUserContextFromToken(token);
    context.set(Constants.USER_CTX_KEY, userContext);
    context.set(Constants.USER_DP_TOKEN, token);

    return null;
  }
}
