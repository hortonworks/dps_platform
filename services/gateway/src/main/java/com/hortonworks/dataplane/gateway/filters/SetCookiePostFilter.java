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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.utils.CookieUtils;
import com.hortonworks.dataplane.gateway.utils.Jwt;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

@Service
public class SetCookiePostFilter extends ZuulFilter {
  private static final Logger logger = LoggerFactory.getLogger(SetCookiePostFilter.class);

  @Autowired
  private Jwt jwt;

  @Autowired
  private CookieUtils cookieUtils;


  @Override
  public String filterType() {
    return POST_TYPE;
  }


  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 8;
  }


  @Override
  public boolean shouldFilter() {
    RequestContext context = RequestContext.getCurrentContext();

    return context.get(Constants.USER_CTX_KEY) != null && context.get(Constants.USER_DP_TOKEN) == null;
  }

  @Override
  public Object run() {

    RequestContext context = RequestContext.getCurrentContext();
    UserContext userContext = (UserContext) context.get(Constants.USER_CTX_KEY);

    // TODO: a better solution would have been to remove password from generated json, but the same code is being used to generate token for cb.
    userContext.setPassword("");
    String token = jwt.makeJWT(userContext);

    Cookie cookie = cookieUtils.buildNewCookie(Constants.DP_JWT_COOKIE, token, jwt.getExpiration(token));
    context.getResponse().addCookie(cookie);

    return null;
  }

}
