/*
 *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *   properly licensed third party, you do not have any rights to this code.
 *
 *   If this code is provided to you under the terms of the AGPLv3:
 *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *     FROM OR RELATED TO THE CODE; AND
 *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 */

package com.hortonworks.datapalane.consul.model;


public class ConsulEvent {
  private String name;
  private String serviceName;
  private String payload;
/*
Name is the event name. Dont being with underscore.
servicename is optional and is used to filter the event to which service the event will be sent
payload is optinal and would be sent as part of event. if payload is specified it should be less than 100 bytes.
 */
  public ConsulEvent(String name, String serviceName, String payload) {
    if (name==null || name.startsWith("_")){
      throw new RuntimeException("invalid event");
    }
    this.name = name;
    this.serviceName = serviceName;
    this.payload = payload;
  }

  public String getName() {
    return name;
  }

  public String getServiceName() {
    return serviceName;
  }

  public String getPayload() {
    return payload!=null?payload:"";
  }

  @Override
  public String toString() {
    return "ConsulEvent{" +
      "name='" + name + '\'' +
      ", serviceName='" + serviceName + '\'' +
      ", payload='" + payload + '\'' +
      '}';
  }
}
