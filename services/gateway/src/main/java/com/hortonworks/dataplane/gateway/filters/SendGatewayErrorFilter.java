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
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.utils.CookieUtils;
import com.hortonworks.dataplane.gateway.utils.Utils;
import com.netflix.zuul.context.RequestContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.post.SendErrorFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SEND_ERROR_FILTER_ORDER;


@Service
public class SendGatewayErrorFilter extends SendErrorFilter {

	private static final Log logger = LogFactory.getLog(SendGatewayErrorFilter.class);

  @Autowired
  private Utils utils;

  @Autowired
  private CookieUtils cookieUtils;

	@Override
	public int filterOrder() {
		return SEND_ERROR_FILTER_ORDER - 1;
	}

	@Override
	public boolean shouldFilter() {
		RequestContext ctx = RequestContext.getCurrentContext();
		return ctx.getThrowable() != null && ctx.getThrowable().getCause() instanceof GatewayException;
	}

	@Override
	public Object run() {
		try {
			RequestContext ctx = RequestContext.getCurrentContext();
			GatewayException exception = (GatewayException) ctx.getThrowable().getCause();

			if(exception.getStatus() == HttpStatus.UNAUTHORIZED) {
        // set sign-in uri in header
        ctx.getResponse().addHeader(Constants.AUTH_HEADER_CHALLENGE_ADDRESS, utils.getSignInUri());

        // clear cookies
        Cookie[] cookies = ctx.getRequest().getCookies();
        if (cookies != null) {
          for (Cookie cookie : cookies) {
            if (cookieUtils.isRelevant(cookie.getName())) {
              Cookie clearCookie = cookieUtils.buildDeleteCookie(cookie, utils.getRequestHost(ctx.getRequest()));
              ctx.getResponse().addCookie(clearCookie);
            }
          }
        }
      }
		} catch (Exception ex) {
			logger.warn("Unable to get a valid HTTP status code.", ex);
		}
		return null;
	}

}
