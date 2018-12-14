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


import com.google.common.base.Optional;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.service.BlacklistCache;
import com.hortonworks.dataplane.gateway.utils.CookieUtils;
import com.hortonworks.dataplane.gateway.utils.Jwt;
import com.netflix.util.Pair;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

import static com.hortonworks.dataplane.gateway.domain.Constants.DP_INVALIDATION_HEADER_KEY;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

@Service
public class TokenInvalidationPostFilter extends ZuulFilter {
  private static final Logger logger = LoggerFactory.getLogger(TokenInvalidationPostFilter.class);

  @Autowired
  private CookieUtils cookieUtils;

  @Autowired
  Jwt jwt;

  @Autowired
  private BlacklistCache blacklistedTokenCheckService;

  @Override
  public String filterType() {
    return POST_TYPE;
  }


  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 7;
  }


  @Override
  public boolean shouldFilter() {
    RequestContext context = RequestContext.getCurrentContext();

    // Check if it is a change password call
    return context.getOriginResponseHeaders()
      .stream()
      .filter(header -> header.first().equalsIgnoreCase(DP_INVALIDATION_HEADER_KEY))
      .findAny()
      .map(header -> true)
      .orElse(false);
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

    if(token != null) {
      Optional<DateTime> cookieExpiry = Optional.fromNullable(new DateTime(jwt.getExpiration(token)));
      if(cookieExpiry.isPresent()) {
        blacklistedTokenCheckService.blackList(token, cookieExpiry.get());
        blacklistedTokenCheckService.markForRefresh(jwt.parseJWT(token).getUsername());
      }
    }

    return null;
  }

}
