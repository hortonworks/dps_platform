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
import com.hortonworks.dataplane.gateway.domain.NoAllowedGroupsException;
import com.hortonworks.dataplane.gateway.domain.TokenInfo;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.service.BlacklistCache;
import com.hortonworks.dataplane.gateway.service.UserService;
import com.hortonworks.dataplane.gateway.utils.CookieUtils;
import com.hortonworks.dataplane.gateway.utils.KnoxSso;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_DECORATION_FILTER_ORDER;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.PRE_TYPE;


@Service
public class KnoxAuthAndSyncPreFilter extends ZuulFilter {
  private static final Logger logger = LoggerFactory.getLogger(KnoxAuthAndSyncPreFilter.class);


  @Autowired
  private KnoxSso knox;

  @Autowired
  private UserService userService;

  @Autowired
  private BlacklistCache blacklistCache;

  @Autowired
  private CookieUtils cookieUtils;

  private static final Long MILLISECONDS_IN_1_MINUTE = 1L * 60 * 1000;

  @Value("${ldap.groups.resync.interval.minutes}")
  private Long ldapResyncInterval;

  @Override
  public String filterType() {
    return PRE_TYPE;
  }

  @Override
  public int filterOrder() {
    return PRE_DECORATION_FILTER_ORDER + 4;
  }

  @Override
  public boolean shouldFilter() {
    RequestContext context = RequestContext.getCurrentContext();
    String knoxToken = cookieUtils.getKnoxToken();

    return context.get(Constants.USER_CTX_KEY) == null && knoxToken != null;
  }

  @Override
  public Object run() {
    RequestContext context = RequestContext.getCurrentContext();
    String knoxToken = cookieUtils.getKnoxToken();
    UserContext userContext = buildSyncedUserContextFromKnoxToken(knoxToken, context.getRequest().getServletPath().endsWith(Constants.USER_IDENTITY));

    context.set(Constants.USER_CTX_KEY, userContext);

    return null;
  }


  private UserContext buildSyncedUserContextFromKnoxToken(String token, boolean isIdentity) throws GatewayException {
    UserContext userContext = null;
    TokenInfo tokenInfo = knox.validateJwt(token);

    if (blacklistCache.isBacklisted(token)) {
      throw new GatewayException(HttpStatus.UNAUTHORIZED, "Knox token was blacklisted");
    }

    if (!tokenInfo.isValid()) {
      throw new GatewayException(HttpStatus.UNAUTHORIZED, "Knox token is invalid.");
    }

    Optional<UserContext> userContextFromDb = userService.getUserContext(tokenInfo.getSubject());

    if (!userContextFromDb.isPresent() && isIdentity) {
      try {
        userContext = userService.syncUserFromLdapGroupsConfiguration(tokenInfo.getSubject());
      } catch (NoAllowedGroupsException nge) {
        throw new GatewayException(HttpStatus.FORBIDDEN, "User does not have access to DPS. No groups have been configured.");
      }
    } else if(userContextFromDb.isPresent()) {

      userContext = userContextFromDb.get();

      if (!userContext.isActive()) {
        throw new GatewayException(HttpStatus.FORBIDDEN, "User has been marked inactive.");
      }

      if (needsResyncFromLdap(userContext)) {
        logger.info(String.format("resyncing from ldap for user [%s]", tokenInfo.getSubject()));
        userContext = userService.resyncUserFromLdapGroupsConfiguration(tokenInfo.getSubject());
        if (userContext == null) {
          throw new GatewayException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to resync user with LDAP.");
        }
        logger.info(("resync complete"));
      }
    } else if(userContext == null) {
        throw new GatewayException(HttpStatus.FORBIDDEN, "User does not have access to DPS.");
    }
    return userContext;
  }

  private boolean needsResyncFromLdap(UserContext userContextFromDb) {
    return userContextFromDb.isGroupManaged() && System.currentTimeMillis() - userContextFromDb.getUpdatedAt() > ldapResyncInterval * MILLISECONDS_IN_1_MINUTE;
  }
}
