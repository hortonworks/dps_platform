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

package com.hortonworks.dataplane.gateway.exceptions;

import com.netflix.zuul.exception.ZuulException;
import org.springframework.cloud.netflix.zuul.util.ZuulRuntimeException;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletResponse;

public class GatewayException extends ZuulRuntimeException {

  public GatewayException(HttpStatus status, String message) {
    super(new ZuulException("Gateway exception", status.value(), message));
  }

  public GatewayException(Throwable throwable, HttpStatus status, String message) {
    super(new ZuulException(throwable, "Gateway exception", status.value(), message));
  }

  public HttpStatus getStatus() {
    ZuulException exception = findZuulException(this);
    return HttpStatus.valueOf(exception.nStatusCode);
  }

  private ZuulException findZuulException(Throwable throwable) {
    if (throwable.getCause() instanceof ZuulRuntimeException) {
      // this was a failure initiated by one of the local filters
      return (ZuulException) throwable.getCause().getCause();
    }

    if (throwable.getCause() instanceof ZuulException) {
      // wrapped zuul exception
      return (ZuulException) throwable.getCause();
    }

    if (throwable instanceof ZuulException) {
      // exception thrown by zuul lifecycle
      return (ZuulException) throwable;
    }

    // fallback, should never get here
    return new ZuulException(throwable, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, null);
  }
}
