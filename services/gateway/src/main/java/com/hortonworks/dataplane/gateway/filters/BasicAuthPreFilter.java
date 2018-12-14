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
import com.hortonworks.dataplane.gateway.domain.Credential;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.Base64;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;


@Service
public class BasicAuthPreFilter extends ZuulFilter {

  @Autowired
  private AuthenticationService authenticationService;

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
    String serviceId = context.get(SERVICE_ID_KEY).toString();
    if(serviceId.equals(Constants.HDP_PROXY))
      return false;
    HttpServletRequest request = context.getRequest();
    String authHeader = request.getHeader(Constants.AUTHORIZATION_HEADER);
    return context.get(Constants.USER_CTX_KEY) == null && (authHeader != null && authHeader.startsWith(Constants.AUTH_HEADER_PRE_BASIC));
  }

  @Override
  public Object run() {
    RequestContext context = RequestContext.getCurrentContext();
    HttpServletRequest request = context.getRequest();
    String authHeader = request.getHeader(Constants.AUTHORIZATION_HEADER);

    Credential credential = getCredentialFromBasicAuthHeader(authHeader);
    UserContext userContext = authenticationService.buildUserContextFromCredential(credential);
    context.set(Constants.USER_CTX_KEY, userContext);

    return null;
  }

  private Credential getCredentialFromBasicAuthHeader(String header) throws GatewayException {
    String encoded = header.substring(Constants.AUTH_HEADER_PRE_BASIC.length());
    String[] partsOfcredential = new String(Base64.getDecoder().decode(encoded), Charset.forName("UTF-8")).split(":",2);

    Credential credential = new Credential();
    credential.setUsername(partsOfcredential[0]);
    credential.setPassword(partsOfcredential[1]);

    return credential;
  }
}
