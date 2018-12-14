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

package com.hortonworks.datapalane.consul;

public class ZuulServer {

  private final String ip;
  private final int port;
  private final String node;

  public ZuulServer(String ip, int port, String node) {
    this.ip = ip;
    this.port = port;
    this.node = node;
  }

  public String getIp() {
    return ip;
  }

  public int getPort() {
    return port;
  }


  @Override
  public String toString() {
    return "ZuulServer{" +
      "ip='" + ip + '\'' +
      ", port=" + port +
      ", node='" + node + '\'' +
      '}';
  }

  public String getNode() {
    return node;
  }

  public String makeUrl(boolean ssl) {
    String protocol = ssl?"https":"http";
    return protocol+"://"+ip+":"+port;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ZuulServer that = (ZuulServer) o;

    if (port != that.port) return false;
    if (!ip.equals(that.ip)) return false;
    return node.equals(that.node);
  }

  @Override
  public int hashCode() {
    int result = ip.hashCode();
    result = 31 * result + port;
    result = 31 * result + node.hashCode();
    return result;
  }
}
