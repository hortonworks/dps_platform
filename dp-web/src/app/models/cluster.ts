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

import {Location} from "./location";
export class Cluster {
  id: number;
  name: string;
  description: string = '';
  dcName: string;
  ambariurl: string;
  ipAddress: string;
  services: string[] = [];
  location: Location;
  tags: string[] = [];
  ambariuser: string;
  ambaripass: string;
  secured: boolean;
  kerberosuser: string;
  kerberosticketLocation: string;
  datalakeid: number;
  userid: number;
  dataplaneClusterId: number;
  properties: any;
  knoxUrl?:string;

}

export class ClusterHealthSummary {
  nodes: number;
  size: string;
  totalSize: string;
  usedSize: string;
  status: {
    state: string,
    since: number
  };
}

export class ClusterDetails {
 nodes: number;
 size: string;
 securityType: number;
 location: string;
 dataCenter: string;
 noOfSerices: number;
 heapSizeUsed: string;
 heapSizeTotal: string;
 healthyNodes: number;
 hdfsTotal: string;
 hdfsUsed: string;
 hdpVersion: string;
 unhealthyNodes: number;
 networkUsage: number;
 ldapUrl: string;
 tags: string;
 uptime: string;
 status: string;
 nodeManagersActive: number;
 nodeManagersInactive: number;
 rmHeapUsed: string;
 rmHeapTotal: string;
 rmUptime: string;
 healthyDataNodes: number;
 unhealthyDataNodes: number;
}

export class ServiceInfo {
  serviceName: string;
  state: string;
  serviceVersion: string;
}
