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
package com.hortonworks.dataplane.gateway.config;


import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.google.common.collect.Lists;
import com.hortonworks.dataplane.gateway.domain.Constants;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;


/**
 * Sets up cloudbreak registration if enable.cloudbreak is true
 * In HA mode this may cause multiple registrations which in this case
 * is fine
 */
@Component
public class CloudbreakRegistration {

  public static final String CONSUL = "consul";
  private static Logger log = LoggerFactory.getLogger(CloudbreakRegistration.class);

  public static final String DP_GATEWAY_SERVICES_CLOUDBREAK = "dp.gateway.services.cloudbreak";

  @Value("${enable.cloudbreak}")
  private Boolean enableCloudBreak = false;

  @Autowired
  private Config config;

  @Autowired(required = false)
  private ConsulClient consulClient;


  @PostConstruct
  public void init() {
    Config c = config.getConfig(DP_GATEWAY_SERVICES_CLOUDBREAK);
    String mode = config.getString("dp.gateway.mode");
    if (!enableCloudBreak && mode.equals(CONSUL)) {
      // Remove any previous registrations
      consulClient.agentServiceDeregister(c.getString("id"));
      return;
    }
    if (!mode.equals(CONSUL)) {
      return;
    }
    log.info("Cloudbreak is enabled through enable.cloudbreak, registering a new service");
    NewService newService = new NewService();
    newService.setAddress(c.getString("host"));
    newService.setPort(c.getInt("port"));
    newService.setId(c.getString("id"));
    newService.setName(c.getString("id"));
    newService.setTags(Lists.newArrayList(c.getString("id"), Constants.DP_PLUGIN_APP));
    NewService.Check serviceCheck = new NewService.Check();
    serviceCheck.setHttp("http://127.0.0.1:8500/v1/health/service/cloudbreak");
    serviceCheck.setInterval("60s");
    List<NewService.Check> checks = new ArrayList<>();
    checks.add(serviceCheck);
    newService.setChecks(checks);
    consulClient.agentServiceRegister(newService);
  }


  @PreDestroy
  public void deregister() {
    log.info("Removing any registered cloudbreak instances");
    Config c = config.getConfig(DP_GATEWAY_SERVICES_CLOUDBREAK);
    consulClient.agentServiceDeregister(c.getString("id"));

  }


}
