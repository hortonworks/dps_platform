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

import com.google.common.base.Optional;
import com.hortonworks.dataplane.gateway.domain.*;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserService {
  @Autowired
  private UserServiceInterface userServiceInterface;

  @Autowired
  private LdapUserInterface ldapUserInterface;


  public Optional<UserContext> getUserContext(String userName) {
    try {
      UserList list=userServiceInterface.getUserContext(userName);
      UserContext uc = list.getResults();
      uc.setDbManaged(false);
      return Optional.of(uc);
    }catch (FeignException fe){
      if (fe.status()==404){
        return Optional.absent();
      }else{
        throw fe;
      }
    }
  }


  public UserContext syncUserFromLdapGroupsConfiguration(String subject) {
    try {
      UserContext uc = ldapUserInterface.addUserFromLdapGroupsConfiguration(subject);
      uc.setDbManaged(false);
      return uc;
    }catch (FeignException fe){
      if (fe.status()==403){
        throw new NoAllowedGroupsException();//Usaually groups are not configured for this user.
      }else{
        throw fe;
      }
    }
  }
  public UserContext resyncUserFromLdapGroupsConfiguration(String subject) {
    try {
      UserContext uc = ldapUserInterface.resyncUserFromLdapGroupsConfiguration(subject);
      uc.setDbManaged(false);
      return uc;
    }catch (FeignException fe){
      if (fe.status()==403){
        throw new NoAllowedGroupsException();//Usaually groups are not configured for this user.
      }else{
        throw fe;
      }
    }
  }

}
