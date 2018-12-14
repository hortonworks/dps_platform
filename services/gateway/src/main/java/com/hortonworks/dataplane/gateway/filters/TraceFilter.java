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
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.typesafe.config.Config;
import org.apache.commons.collections.EnumerationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.POST_TYPE;
import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;


/**
 * This filter logs API calls and responses across
 * all services which return a 500
 */
@Service
public class TraceFilter extends ZuulFilter {

  private static final int NINE_NINETY_NINE = 999;
  private static Logger log = LoggerFactory.getLogger(TraceFilter.class);

  private static final List<String> REDACTED_HEADERS = Arrays.asList(Constants.COOKIE_HEADER.toLowerCase(), Constants.AUTHORIZATION_HEADER.toLowerCase(), Constants.DP_TOKEN_INFO_HEADER_KEY.toLowerCase(), Constants.DP_USER_INFO_HEADER_KEY.toLowerCase());

  @Autowired
  private Config config;

  @Override
  public String filterType() {
    return POST_TYPE;
  }


  @Override
  public int filterOrder() {
    return NINE_NINETY_NINE;
  }

  /**
   * @return True if 500 response
   */
  @Override
  public boolean shouldFilter() {
    RequestContext ctx = RequestContext.getCurrentContext();
    return ctx.getResponseStatusCode() >= 500;
  }

  /**
   * In case the API request returned a Http 500
   * log the response by copying the body
   *
   */
  @Override
  public Object run() {
    log.info("Logging API response");
    RequestContext ctx = RequestContext.getCurrentContext();
    HttpServletRequest request = ctx.getRequest();
    Enumeration<String> headerNames = request.getHeaderNames();
    log.info("Logging Request headers");
    EnumerationUtils
      .toList(headerNames)
      .stream()
      .forEach((Consumer<String>) s -> log.warn("Header "+ s + "->"+ (config.getString("env").equals("production") && REDACTED_HEADERS.contains(s.toLowerCase()) ? "*redacted*" : request.getHeader(s))));
    log.info("Logging Zuul request headers");
    ctx
      .getZuulRequestHeaders()
      .forEach((s, s2) -> log.warn(s +"->"+ (config.getString("env").equals("production") && REDACTED_HEADERS.contains(s.toLowerCase()) ? "*redacted*" : s2)));

    log.warn("Request path :" + request.getServletPath());
    try {
      InputStream stream = ctx.getResponseDataStream();
      byte[] body = null;
      if (stream != null) {
        body = StreamUtils.copyToByteArray(stream);
        log.warn("Dumping response body");
        log.warn(new String(body));
      }
      if (body != null) {
        ctx.setResponseDataStream(new ByteArrayInputStream(body));
      }
    } catch (IOException e) {
      rethrowRuntimeException(e);
    }
    return null;
  }
}
