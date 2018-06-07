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
package com.hortonworks.dataplane.gateway.service;

import com.hortonworks.dataplane.gateway.domain.DPConfig;
import com.hortonworks.dataplane.gateway.domain.KnoxConfigurationResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationService {
  private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);

  @Autowired
  private ConfigurationServiceInterface configurationServiceInterface;

  @Value("${jwt.validity.minutes}")
  private Long defaultJwtValidity;

  private static final String DP_SESSION_TIMEOUT_KEY = "dp.session.timeout.minutes";

  public boolean isLdapConfigured() {
    try {
      KnoxConfigurationResponse knoxStatus = configurationServiceInterface.getKnoxStatus();
      return knoxStatus.getConfigured();
    } catch (FeignException e) {
      logger.error("error while calling configuration service", e);
      throw new RuntimeException(e);//TODO should we just send false?
    }
  }


  public Long getJwtTokenValidity() {
    try {
      DPConfig validity = configurationServiceInterface.getTokenValidity(DP_SESSION_TIMEOUT_KEY);
      if (null == validity || validity.getValue().isEmpty()) {
        return defaultJwtValidity;
      }
      return Long.parseLong(validity.getValue());
    } catch (FeignException e) {
      logger.error("error while calling configuration service", e);
      throw new RuntimeException(e);
    }
  }
}
