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


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.service.ConfigurationService;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Component
public class Jwt {
  private static final Logger logger = LoggerFactory.getLogger(Jwt.class);
  public static final String USER_CLAIM = "user";

  private static SignatureAlgorithm sa = SignatureAlgorithm.RS256;
  private static String issuer = "data_plane";

  private static int MINUTE = 60 * 1000;

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private GatewayKeystore gatewayKeystore;

  public String makeJWT(UserContext userContext) throws GatewayException {
    long timeMillis = System.currentTimeMillis();
    Date now = new Date(timeMillis);
    Map<String, Object> claims = Maps.newHashMap();
    try {
      claims.put(USER_CLAIM, mapper.writeValueAsString(userContext));
    } catch (JsonProcessingException ex) {
      throw new GatewayException(ex, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize user context.");
    }

    Long jwtValidity = this.configurationService.getJwtTokenValidity();
    JwtBuilder builder = Jwts.builder()
      .setIssuedAt(now)
      .setIssuer(issuer)
      .setSubject(userContext.getUsername())
      .setClaims(claims)
      .setExpiration(new Date(now.getTime() + jwtValidity * MINUTE))
      .signWith(sa, getSigningKey());

    return builder.compact();

  }

  private Claims parseAndGetClaims(String jwt)
      throws GatewayException {
    try {

      return Jwts.parser()
        .setSigningKey(getVerifyingKey())
        .parseClaimsJws(jwt)
        .getBody();

    } catch (ExpiredJwtException ex) {
      logger.error("token expired", ex);
      throw new GatewayException(HttpStatus.UNAUTHORIZED, "Token has expired.");
    } catch (JwtException e) {
      logger.error("Jwt Exception", e);
      throw new GatewayException(HttpStatus.UNAUTHORIZED, "Token was invalid.");
    }
  }

  public UserContext parseJWT(String jwt)
    throws GatewayException {

    try {
      Claims claims = parseAndGetClaims(jwt);

      String userJsonString = claims.get(USER_CLAIM).toString();
      UserContext userContext = mapper.readValue(userJsonString, UserContext.class);

      return userContext;

    } catch (IOException e) {
      logger.error("Exception", e);
      throw new GatewayException(HttpStatus.UNAUTHORIZED, "Token was invalid.");
    }
  }

  public Date getExpiration(String jwt)
    throws GatewayException {

    Claims claims = parseAndGetClaims(jwt);
    return claims.getExpiration();
  }

  private Key getSigningKey() {
    return gatewayKeystore.getPrivate();
  }

  private Key getVerifyingKey() {
    return gatewayKeystore.getPublic();
  }

}
