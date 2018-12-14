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
import com.hortonworks.dataplane.gateway.domain.TokenInfo;
import io.jsonwebtoken.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;


@Component
public class KnoxSso {
  private static final Logger logger = LoggerFactory.getLogger(KnoxSso.class);


  @Value("${sso.enabled}")
  private Boolean isSsoEnabled;

  @Value("${signing.pub.key.path}")
  private String publicKeyPath;


  @Value("${knox.websso.path}")
  private String knoxWebssoPath;

  private Optional<PublicKey> signingKey=Optional.absent();

  @PostConstruct
  public void init() {
    configureSigningKey();
  }

  public TokenInfo validateJwt(String token) {
    TokenInfo tokenInfo=new TokenInfo();
    try {
      Claims claims = Jwts.parser()
        .setSigningKey(signingKey.get())
        .parseClaimsJws(token)
        .getBody();
      tokenInfo.setClaims(claims);
      return tokenInfo;
    }catch(ExpiredJwtException ex){
      logger.info("knox sso cookie has expired");
      return tokenInfo;
    }catch (JwtException e){
      logger.error("knox sso validate exception", e);
      return tokenInfo;
    }
  }

  private void configureSigningKey() {
    if (!isSsoEnabled){
      return;
    }
    try {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      ByteArrayInputStream is = new ByteArrayInputStream(getPublicKeyString().getBytes("UTF8"));
      X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
      this.signingKey = Optional.of(cer.getPublicKey());
    } catch (IOException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    } catch (CertificateException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    }
  }

  private String getPublicKeyString() {
    try {
      if (!StringUtils.isBlank(getPublicKeyPath())) {
        return FileUtils.readFileToString(new File(getPublicKeyPath()));
      } else {
        throw new RuntimeException("Public certificate of knox must be configured");
      }
    } catch (IOException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    }
  }

  private String getPublicKeyPath() {
    return publicKeyPath;
  }
}
