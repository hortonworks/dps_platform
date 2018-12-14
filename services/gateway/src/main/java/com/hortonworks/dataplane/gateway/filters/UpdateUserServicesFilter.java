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
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.POST_TYPE;
import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;


/**
 * This filter modifies the reponse of the identity call when cloudbreak is enabled in dataplane
 */
@Service
public class UpdateUserServicesFilter extends ZuulFilter {

  private static final int NINE_NINETY_EIGHT = 998;
  private static Logger log = LoggerFactory.getLogger(UpdateUserServicesFilter.class);


  @Value("${enable.cloudbreak}")
  private Boolean enableCloudBreak = false;

  @Autowired
  private ObjectMapper mapper;

  @Override
  public String filterType() {
    return POST_TYPE;
  }


  @Override
  public int filterOrder() {
    return NINE_NINETY_EIGHT;
  }

  /**
   * @return True if identity call and cloudbreak is enabled
   */
  @Override
  public boolean shouldFilter() {
    RequestContext ctx = RequestContext.getCurrentContext();
    return ctx.getRequest().getRequestURI().contains("/service/core/api/identity") && enableCloudBreak;
  }

  /**
   * Overwrite the response with cloudbreak as a enabled service
   */
  @Override
  public Object run() {
    log.info("Rewriting identity request when cloudbreak is enabled");
    RequestContext ctx = RequestContext.getCurrentContext();
    try {
      InputStream stream = ctx.getResponseDataStream();
      if (stream != null) {
        byte[] body = StreamUtils.copyToByteArray(stream);
        UserContext userContext = mapper.readValue(body, UserContext.class);
        List<String> services = userContext.getServices();
        services.add(Constants.CLOUDBREAK);
        userContext.setServices(services);
        log.info("Rewriting response data stream");
        ctx.setResponseDataStream(new ByteArrayInputStream(mapper.writeValueAsBytes(userContext)));
        stream.close();
      }
    } catch (IOException e) {
      rethrowRuntimeException(e);
    }
    return null;
  }
}
