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
package com.hortonworks.dataplane.gateway.auth;

import com.hortonworks.dataplane.gateway.domain.Constants;
import com.hortonworks.dataplane.gateway.domain.Credential;
import com.hortonworks.dataplane.gateway.domain.UserContext;
import com.hortonworks.dataplane.gateway.exceptions.GatewayException;
import com.hortonworks.dataplane.gateway.utils.CookieUtils;
import com.hortonworks.dataplane.gateway.utils.Jwt;
import com.hortonworks.dataplane.gateway.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

  private static Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

  @Autowired
  private AuthenticationService authenticationService;

  @Autowired
  private CookieUtils cookieUtils;

  @Autowired
  private Jwt jwt;

  @Autowired
  private Utils utils;

  @RequestMapping(path="/in", method = RequestMethod.POST)
  public ResponseEntity<?> signIn(@RequestBody Credential credential, HttpServletResponse response) {

    try {
      UserContext userContext = authenticationService.getUserContextFromCredential(credential);

      String token = jwt.makeJWT(userContext);
      Cookie cookie = cookieUtils.buildNewCookie(Constants.DP_JWT_COOKIE, token, jwt.getExpiration(token));
      response.addCookie(cookie);

      return new ResponseEntity<Object>(userContext, HttpStatus.OK);
    } catch (GatewayException ex) {
      return new ResponseEntity<String>(ex.getStatus().getReasonPhrase(), ex.getStatus());
    }
  }

  @RequestMapping(path="/out", method = RequestMethod.GET)
  public Object signOut(HttpServletRequest request, HttpServletResponse response) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if(cookieUtils.isRelevant(cookie.getName())) {
          cookieUtils.blackList(cookie);
          Cookie clearCookie = cookieUtils.buildDeleteCookie(cookie, utils.getRequestHost(request));
          response.addCookie(clearCookie);
        }
      }
    }

    response.addHeader(Constants.AUTH_HEADER_CHALLENGE_ADDRESS, utils.getSignInUri());

    return new HashMap<String, String>();
  }
}
