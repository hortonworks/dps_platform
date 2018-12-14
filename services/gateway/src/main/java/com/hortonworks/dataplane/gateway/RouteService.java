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
package com.hortonworks.dataplane.gateway;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hortonworks.dataplane.gateway.ext.ExcludeContextScan;
import com.netflix.http4.ssl.AcceptAllSocketFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import javax.crypto.Cipher;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import static org.springframework.cloud.commons.httpclient.ApacheHttpClientConnectionManagerFactory.HTTPS_SCHEME;
import static org.springframework.cloud.commons.httpclient.ApacheHttpClientConnectionManagerFactory.HTTP_SCHEME;


@ComponentScan(excludeFilters = {
  @ComponentScan.Filter(type = FilterType.ANNOTATION, value = ExcludeContextScan.class)
})
@SpringBootApplication
@EnableZuulProxy
@EnableDiscoveryClient
@EnableFeignClients
public class RouteService {
  private static final Logger logger = LoggerFactory.getLogger(RouteService.class);
  public static final String CONSUL = "consul";

  public static void main(String[] args) {

    Config config = ConfigFactory.load();

    ConfigurableApplicationContext ctx = makeContext(args,config.getString("dp.gateway.mode "));
    try {
      int maxKeyLen = Cipher.getMaxAllowedKeyLength("AES");
      if(maxKeyLen < 256) {
        logger.error("JCE is not enabled. Shutting down.");
        ctx.close();
      }
    } catch (NoSuchAlgorithmException e) {
      logger.error("Invalid crypto state. Shutting down.", e);
      ctx.close();
    }
  }

  private static ConfigurableApplicationContext makeContext(String[] args, String mode) {
    if(mode.equals(CONSUL))
      return new SpringApplicationBuilder(RouteService.class).profiles("zuul").web(true).run(args);
    else
      return new SpringApplicationBuilder(RouteService.class).profiles("k8s").web(true).run(args);
  }


  @Bean
  public ObjectMapper loadObjectMapper(){
    return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Enables typesafe config from application.conf
   * @return
   */
  @Bean
  public Config loadConfig() {
    return ConfigFactory.load();
  }

  @Bean
  public RegistryBuilder registryBuilder() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    return RegistryBuilder.<ConnectionSocketFactory> create()
      .register(HTTP_SCHEME, PlainConnectionSocketFactory.INSTANCE)
      //TODO: Ashwin Attach a secure version after allowing cert files to be imported
      .register(HTTPS_SCHEME, new AcceptAllSocketFactory());
  }


}
