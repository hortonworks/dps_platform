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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

@Component
public class GatewayKeystore {
  private static final Logger logger = LoggerFactory.getLogger(GatewayKeystore.class);

  private static PrivateKey privateKey;
  private static PublicKey publicKey;

  @Value("${jwt.public.key.path}")
  private String publicKeyPath;
  @Value("${jwt.private.key.path}")
  private String privateKeyPath;
  @Value("${jwt.private.key.password}")
  private String privateKeyPassword;

  public PrivateKey getPrivate(){
    if(privateKey == null) {
      privateKey = readPrivate();
    }

    return  privateKey;
  }

  public PublicKey getPublic(){
    if(publicKey == null) {
      publicKey = readPublic();
    }

    return publicKey;
  }

  private PrivateKey readPrivate() {
    try {
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(getKeyFileAsByteArray(this.privateKeyPath));
      KeyFactory kf = KeyFactory.getInstance("RSA");
      return kf.generatePrivate(spec);
    } catch (NoSuchAlgorithmException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    } catch (InvalidKeySpecException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    }
  }

  private PublicKey readPublic() {
    try {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      ByteArrayInputStream is = new ByteArrayInputStream(getKeyFileAsByteArray(this.publicKeyPath));
      X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
      return cer.getPublicKey();
    } catch (CertificateException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    }
  }

  private byte[] getKeyFileAsByteArray(String path) {
    try {
      if (!StringUtils.isBlank(path)) {
        return FileUtils.readFileToByteArray(new File(path));
      } else {
        throw new RuntimeException("File path was not supplied.");
      }
    } catch (IOException e) {
      logger.error("Exception", e);
      throw new RuntimeException(e);
    }
  }
}
