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
package com.hortonworks.dataplane.gateway.permissions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class PermPoliciesService {
  private static final Logger logger = LoggerFactory.getLogger(PermPoliciesService.class);

  @Value("${permission.policies}")
  private String permissionPolices;
  @Autowired
  private Environment env;
  @Autowired
  private ResourceLoader resourceLoader;

  private HashMap<String, PermPolicy> policies = new HashMap<>();
  private ObjectMapper jsonMapper = new ObjectMapper();

  @PostConstruct
  public void init() {
    String[] apps=permissionPolices.split(",");
    for(String app:apps){
      String policyFile=env.getProperty(String.format("permission.policy.%s",app));
      Resource resource = resourceLoader.getResource(String.format("classpath:%s" , policyFile));
      try {
        registerPolicy(app,resource.getInputStream());
        logger.info(String.format("Registered policy for: %s",app));
      } catch (IOException e) {
        logger.error("could not read policy file"+app,e);
      }
    }
  }

  public void registerPolicy(String serviceId, InputStream policyStream) {
    try {
      PermPolicy permPolicies = jsonMapper.readValue(policyStream, PermPolicy.class);
      List<RoutePerm> routePerms = permPolicies.getRoutePerms();
      routePerms.sort(Comparator.comparing(RoutePerm::getPath));
      policies.put(serviceId, permPolicies);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean hasPolicy(String serviceId) {
    return policies.containsKey(serviceId);
  }


  public boolean isAuthorized(String serviceId,String method,String inputPath,String[] roles){
    PermPolicy permPolicy = policies.get(serviceId);
    int longestMatch=0;
    RoutePerm bestMatch=null;
    for(RoutePerm rPerm:permPolicy.getRoutePerms()) {
      if (inputPath.startsWith(rPerm.getPath())) {
        int rpermPathLength = rPerm.getPath().length();
        if (rpermPathLength > longestMatch) {
          longestMatch = rpermPathLength;
          bestMatch = rPerm;
        }

      }
    }
    if (bestMatch!=null){
      if (Arrays.asList(bestMatch.getRoles()).contains("*")){
        return  true;
      }
      List<String> policyRoles = Arrays.asList(bestMatch.getRoles());
      Collection intersection = CollectionUtils.intersection(policyRoles, Arrays.asList(roles));
      return !intersection.isEmpty();
    }else{
      return true;
    }
  }
}
