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

import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PermissionsTest  {

  @Test
  public void testAllowedForPATH(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dss",is);
    boolean allowed=permPoliciesService.isAuthorized("dss","GET","/",new String[]{"steward"});

    assertTrue("should be allowed",allowed);
  }

  @Test
  public void testNotAllowedForInvalidPerm(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dpapp",is);
    boolean allowed=permPoliciesService.isAuthorized("dpapp","GET","/blah",new String[]{"admin"});

    assertFalse("should not be allowed",allowed);
  }
  @Test
  public void testAllowedForSubPATH(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dss",is);
    boolean allowed=permPoliciesService.isAuthorized("dss","GET","/replicate",new String[]{"steward"});

    assertTrue("should be allowed",allowed);
  }

  @Test
  public void testNotAllowedForSecretwork(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dss",is);
    boolean allowed=permPoliciesService.isAuthorized("dss","GET","/secret",new String[]{"steward"});

    assertFalse("should not be allowed",allowed);
  }

  @Test
  public void testNotAllowedForSecretKey(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dss",is);
    boolean allowed=permPoliciesService.isAuthorized("dss","GET","/secret/key",new String[]{"steward"});

    assertFalse("should not be allowed",allowed);
  }

  @Test
  public void testForAny(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dss",is);
    boolean allowed=permPoliciesService.isAuthorized("dss","GET","/health",new String[]{});

    assertTrue("should be allowed",allowed);
  }

  @Test
  public void testAnyWithRoles(){
    PermPoliciesService permPoliciesService=new PermPoliciesService();
    InputStream is=loadResource("policyfile1.json");
    permPoliciesService.registerPolicy("dss",is);
    boolean allowed=permPoliciesService.isAuthorized("dss","GET","/health",new String[]{"steward"});

    assertTrue("should be allowed",allowed);
  }


  private InputStream loadResource(String path){
    return this.getClass().getClassLoader().getResourceAsStream(path);
  }
}
