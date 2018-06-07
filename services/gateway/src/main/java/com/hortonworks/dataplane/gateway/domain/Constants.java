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
package com.hortonworks.dataplane.gateway.domain;

public interface Constants {
  String DPAPP = "core";
  String DLMAPP = "dlm";
  String DSSAPP = "dss";
  String CLOUDBREAK = "cloudbreak";
  String HDP_PROXY = "hdp_proxy";

  String DP_USER_INFO_HEADER_KEY = "X-DP-User-Info";
  String DP_TOKEN_INFO_HEADER_KEY = "X-DP-Token-Info";
  String DP_INVALIDATION_HEADER_KEY ="X-Invalidate-Token";
  String AUTH_HEADER_CHALLENGE_ADDRESS = "X-Authenticate-Href";
  String AUTHORIZATION_HEADER = "Authorization";
  String COOKIE_HEADER = "Cookie";

  String AUTH_HEADER_PRE_BASIC = "Basic ";
  String AUTH_HEADER_PRE_BEARER = "Bearer ";

  String DP_JWT_COOKIE = "dp_jwt";

  String DPAPP_BASE_PATH="/service/core";
  String KNOX_CONFIG_PATH = DPAPP_BASE_PATH + "/api/knox/configuration";
  String PERMS_POLICY_ENTRY_POINT = DPAPP_BASE_PATH + "/access/policies";
  String GA_PROPERTIES_PATH = DPAPP_BASE_PATH + "/api/config/dps.ga.tracking.enabled";
  String USER_IDENTITY = DPAPP_BASE_PATH + "/api/identity";

  String LOCAL_SIGNIN_PATH = "sign-in";

  String USER_CTX_KEY = "user_ctx";
  String USER_DP_TOKEN = "user_dp_token";

  String ABORT_FILTER_CHAIN = "ABORT_FILTER_CHAIN";

  String FORBIDDEN_REDIRECT_URL = "/unauthorized";
  String SERVICE_NOT_ENABLED_REDIRECT_URL = "/service-error/0";
}
