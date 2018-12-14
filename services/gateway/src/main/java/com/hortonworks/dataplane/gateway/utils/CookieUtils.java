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
package com.hortonworks.dataplane.gateway.utils;


import com.google.common.base.Optional;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.service.BlacklistCache;
import com.netflix.zuul.context.RequestContext;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CookieUtils {
  private static final Logger logger = LoggerFactory.getLogger(CookieUtils.class);

  @Value("${sso.cookie.name}")
  private String KNOX_SSO_COOKIE;

  @Value("${jwt.cookie.secure.only}")
  private boolean USE_SECURE_ONLY_COOKIE;

  @Autowired
  Jwt jwt;

  @Autowired
  BlacklistCache blacklistCache;

  public String getDataplaneToken() {
    Optional<Cookie> cookie = getCookie(Constants.DP_JWT_COOKIE);
    if (cookie.isPresent()) {
      return cookie.get().getValue();
    }
    return null;
  }

  public String getKnoxToken() {
    Optional<Cookie> cookie = getCookie(KNOX_SSO_COOKIE);
    if (cookie.isPresent()) {
      return cookie.get().getValue();
    }
    return null;
  }

  private Optional<Cookie> getCookie(String cookieName) {
    RequestContext ctx = RequestContext.getCurrentContext();
    Cookie[] cookies = ctx.getRequest().getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (cookie.getName().equals(cookieName)) {
          return Optional.of(cookie);
        }
      }
    }
    return Optional.absent();
  }




  public boolean isRelevant(String name) {
    List<String> cookieNames = Arrays.asList(Constants.DP_JWT_COOKIE, KNOX_SSO_COOKIE);
    return cookieNames.contains(name);
  }

  public Cookie buildDeleteCookie(Cookie cookie, String host) {
    if (cookie.getName().equals(KNOX_SSO_COOKIE) && getKnoxDomainFromUrl(host) != null) {
      cookie.setDomain(getKnoxDomainFromUrl(host));
    }
    cookie.setValue(null);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(0);

    return cookie;
  }

  public Cookie buildNewCookie(String key, String value, Date expiry) {
    Cookie cookie = new Cookie(key, value);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(USE_SECURE_ONLY_COOKIE);
    cookie.setMaxAge((int) ((expiry.getTime() - new Date().getTime()) / 1000));

    return cookie;
  }

  /**
   * copied from knox. this is how knox determines domain for a hadoop cookie.
   */
  private String getKnoxDomainFromUrl(String domain) {
    if (isIp(domain)) {
      return null;
    }
    if (dotOccurrences(domain) < 2) {
      return null;
    }
    int idx = domain.indexOf('.');
    if (idx == -1) {
      idx = 0;
    }
    return domain.substring(idx + 1);
  }

  private int dotOccurrences(String domain) {
    return domain.length() - domain.replace(".", "").length();
  }

  private boolean isIp(String domain) {
    Pattern p = Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    Matcher m = p.matcher(domain);
    return m.find();
  }

  public void blackList(Cookie cookie) {
    Date expiration = jwt.getExpiration(cookie.getValue());
    blacklistCache.blackList(cookie.getValue(), new DateTime(expiration));
  }
}
