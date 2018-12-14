-- HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
-- (c) 2016-2018 Hortonworks, Inc. All rights reserved.
-- This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
-- Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
-- to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
-- properly licensed third party, you do not have any rights to this code.
-- If this code is provided to you under the terms of the AGPLv3:
--   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
-- (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
-- LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
-- (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
-- FROM OR RELATED TO THE CODE; AND
-- (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
-- DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
-- DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
-- OR LOSS OR CORRUPTION OF DATA.

CREATE TABLE IF NOT EXISTS dataplane.roles (
  id      BIGSERIAL PRIMARY KEY,
  name    VARCHAR(32) UNIQUE NOT NULL,
  created TIMESTAMP DEFAULT now(),
  updated TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.users (
  id           BIGSERIAL PRIMARY KEY,
  user_name    VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  avatar       VARCHAR(255),
  group_managed BOOLEAN DEFAULT FALSE,
  active       BOOLEAN   DEFAULT TRUE,
  password     VARCHAR(255),
  created      TIMESTAMP DEFAULT now(),
  updated      TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.users_roles (
  id      BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES dataplane.users (id) NOT NULL,
  role_id BIGINT REFERENCES dataplane.roles (id) NOT NULL,
  UNIQUE (user_id, role_id)

);

CREATE TABLE IF NOT EXISTS dataplane.groups (
  id           BIGSERIAL PRIMARY KEY,
  group_name    VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  active       BOOLEAN   DEFAULT TRUE,
  created      TIMESTAMP DEFAULT now(),
  updated      TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.groups_roles (
  id      BIGSERIAL PRIMARY KEY,
  group_id BIGINT REFERENCES dataplane.groups (id) NOT NULL,
  role_id BIGINT REFERENCES dataplane.roles (id) NOT NULL,
  UNIQUE (group_id, role_id)
);

CREATE TABLE IF NOT EXISTS dataplane.permissions (
  id         BIGSERIAL PRIMARY KEY,
  permission VARCHAR(255) UNIQUE                       NOT NULL,
  role_id    BIGINT REFERENCES dataplane.roles (id)    NOT NULL,
  created    TIMESTAMP DEFAULT now(),
  updated    TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.locations (
  id        BIGSERIAL PRIMARY KEY,
  city      VARCHAR(255)   NOT NULL,
  province  VARCHAR(255),
  country   VARCHAR(255)   NOT NULL,
  iso2      VARCHAR(10)     NOT NULL,
  latitude  DECIMAL(10, 6) NOT NULL,
  longitude DECIMAL(10, 6) NOT NULL
);

-- Represents a logical cluster, the name does not have to be the same as in Ambari

CREATE TABLE IF NOT EXISTS dataplane.dp_clusters (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(255)                               NOT NULL,
  dc_name      VARCHAR(255)                               NOT NULL,
  description  TEXT,
  ambari_url   VARCHAR(255)                               NOT NULL,
  ip_address   VARCHAR(255) UNIQUE                        NOT NULL,
  location_id  BIGINT REFERENCES dataplane.locations (id) NOT NULL,
  created_by   BIGINT REFERENCES dataplane.users (id)     NOT NULL,
  properties   JSON,
  state        VARCHAR(32)                                NOT NULL DEFAULT 'TO_SYNC',
  created      TIMESTAMP                                           DEFAULT now(),
  updated      TIMESTAMP                                           DEFAULT now(),
  is_datalake  BOOLEAN                                             DEFAULT FALSE,
  knox_enabled BOOLEAN                                             DEFAULT FALSE,
  knox_url     VARCHAR(255),

  CONSTRAINT unique_name_and_dc_name_constraint UNIQUE (name, dc_name),
  CHECK (state IN ('TO_SYNC', 'SYNC_IN_PROGRESS', 'SYNCED', 'SYNC_ERROR'))
);


CREATE TABLE IF NOT EXISTS dataplane.discovered_clusters (
  id                       BIGSERIAL PRIMARY KEY,
  name                     VARCHAR(255),
  cluster_url              VARCHAR(255),
  secured                  BOOLEAN DEFAULT FALSE,
  kerberos_user            VARCHAR(255),
  kerberos_ticket_Location TEXT,
  dp_clusterid             BIGINT REFERENCES dataplane.dp_clusters (id) UNIQUE NOT NULL, -- One cluster per DL
  user_id                  BIGINT REFERENCES dataplane.users (id)              NOT NULL, -- The user who created the cluster
  properties               JSON
);


CREATE TABLE IF NOT EXISTS dataplane.cluster_services (
  id           BIGSERIAL PRIMARY KEY,
  service_name VARCHAR(255) NOT NULL,
  properties   JSON,
  cluster_id   BIGINT REFERENCES dataplane.discovered_clusters (id)
);

COMMENT ON TABLE dataplane.cluster_services IS 'Services required for DP discovered from the cluster';


CREATE TABLE IF NOT EXISTS dataplane.cluster_service_hosts (
  id         BIGSERIAL PRIMARY KEY,
  host       VARCHAR(255) NOT NULL,
  service_id BIGINT REFERENCES dataplane.cluster_services (id)
);

COMMENT ON TABLE dataplane.cluster_service_hosts IS 'Service hosts for services listed in cluster_services';


CREATE TABLE IF NOT EXISTS dataplane.workspace (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255)                        NOT NULL UNIQUE,
  source      BIGINT REFERENCES dataplane.dp_clusters(id) NOT NULL,
  description TEXT,
  createdby   BIGINT REFERENCES dataplane.users (id) NOT NULL,
  created     TIMESTAMP DEFAULT now(),
  updated     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.data_asset_workspace (
  asset_type   VARCHAR(50)                                 NOT NULL,
  asset_id     BIGINT                                      NOT NULL,
  workspace_id BIGINT REFERENCES dataplane.workspace (id)  NOT NULL
);

CREATE TABLE IF NOT EXISTS dataplane.notebook_workspace (
  notebook_id     VARCHAR(20)                              NOT NULL,
  name            VARCHAR(255)                             NOT NULL,
  created         TIMESTAMP DEFAULT  now(),
  workspace_id BIGINT REFERENCES dataplane.workspace (id)  NOT NULL
);

CREATE TABLE IF NOT EXISTS dataplane.cluster_hosts (
  id         BIGSERIAL PRIMARY KEY,
  host       VARCHAR(255)                                         NOT NULL,
  ipaddr     VARCHAR(39)                                          NOT NULL,
  status     VARCHAR(32)                                          NOT NULL,
  properties JSON,
  cluster_id BIGINT REFERENCES dataplane.discovered_clusters (id) NOT NULL
);


CREATE TABLE IF NOT EXISTS dataplane.cluster_properties (
  id         BIGSERIAL PRIMARY KEY,
  properties JSON,
  cluster_id BIGINT REFERENCES dataplane.discovered_clusters (id) NOT NULL
);

CREATE TABLE IF NOT EXISTS dataplane.categories (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL UNIQUE,
  description TEXT,
  created     TIMESTAMP DEFAULT now(),
  updated     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.datasets (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(255)                                       NOT NULL,
  description  TEXT,
  dp_clusterid BIGINT REFERENCES dataplane.dp_clusters (id)       NOT NULL,
  createdby    BIGINT REFERENCES dataplane.users (id)             NOT NULL,
  createdon    TIMESTAMP DEFAULT now()                            NOT NULL,
  lastmodified TIMESTAMP DEFAULT now()                            NOT NULL,
  active       BOOLEAN DEFAULT TRUE,
  version      SMALLINT DEFAULT 1,
  custom_props JSON
);

CREATE TABLE IF NOT EXISTS dataplane.dataset_categories (
  category_id BIGINT REFERENCES dataplane.categories (id) ON DELETE CASCADE NOT NULL,
  dataset_id  BIGINT REFERENCES dataplane.datasets (id) ON DELETE CASCADE NOT NULL
);

CREATE TABLE IF NOT EXISTS dataplane.unclassified_datasets (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(255)                                       NOT NULL,
  description  TEXT,
  dp_clusterid BIGINT REFERENCES dataplane.dp_clusters (id)       NOT NULL,
  createdby    BIGINT REFERENCES dataplane.users (id)             NOT NULL,
  createdon    TIMESTAMP DEFAULT now()                            NOT NULL,
  lastmodified TIMESTAMP DEFAULT now()                            NOT NULL,
  custom_props JSON
);

CREATE TABLE IF NOT EXISTS dataplane.unclassified_datasets_categories (
  category_id             BIGINT REFERENCES dataplane.categories (id)            NOT NULL,
  unclassified_dataset_id BIGINT REFERENCES dataplane.unclassified_datasets (id) NOT NULL
);

CREATE TABLE IF NOT EXISTS dataplane.data_asset (
  id               BIGSERIAL PRIMARY KEY,
  asset_type       VARCHAR(100) NOT NULL,
  asset_name       TEXT        NOT NULL,
  guid             VARCHAR(100) NOT NULL,
  asset_properties JSON       NOT NULL,
  dataset_id       BIGINT REFERENCES dataplane.datasets (id) ON DELETE CASCADE DEFAULT NULL,
  cluster_id       BIGINT REFERENCES dataplane.discovered_clusters (id) NOT NULL
);

-- Since datasets are boxes, we will need to store details
CREATE TABLE IF NOT EXISTS dataplane.dataset_details (
  id         BIGSERIAL,
  details    JSON,
  dataset_id BIGINT REFERENCES dataplane.datasets (id) ON DELETE CASCADE
);


CREATE TABLE IF NOT EXISTS dataplane.skus (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  status      SMALLINT  DEFAULT 1, -- enum 1 - Disable, 2 - Setting up, 3 - Active, ...
  created     TIMESTAMP DEFAULT now(),
  updated     TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dataplane.enabled_skus (
  sku_id          BIGINT REFERENCES dataplane.skus (id) UNIQUE NOT NULL,
  enabled_by      BIGINT REFERENCES dataplane.users (id)       NOT NULL,
  enabled_on      TIMESTAMP                                    NOT NULL,
  smartsense_id   TEXT                                         NOT NULL,
  subscription_id TEXT                                         NOT NULL,
  created         TIMESTAMP DEFAULT now(),
  updated         TIMESTAMP DEFAULT now()
);

-- Global DP configurations - could be exported to cluster ZK if needed
CREATE TABLE IF NOT EXISTS dataplane.configs (
  id           BIGSERIAL PRIMARY KEY,
  config_key   VARCHAR(255)          NOT NULL  UNIQUE,
  config_value TEXT                  NOT NULL,
  active       BOOLEAN DEFAULT TRUE  NOT NULL,
  export       BOOLEAN DEFAULT TRUE  NOT NULL
);

CREATE TABLE IF NOT EXISTS dataplane.ldap_configs (
  id          BIGSERIAL PRIMARY KEY,
  url         VARCHAR(255)          NOT NULL,
  bind_dn     VARCHAR(255),
  user_searchbase      VARCHAR(255),
  usersearch_attributename  VARCHAR(255),
  group_searchbase     VARCHAR(255),
  groupsearch_attributename  VARCHAR(255),
  group_objectclass VARCHAR(255),
  groupmember_attributename VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS dataplane.user_groups (
  id           BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES dataplane.users (id) NOT NULL,
  group_id BIGINT REFERENCES dataplane.groups (id) NOT NULL,
  created      TIMESTAMP DEFAULT now(),
  updated      TIMESTAMP DEFAULT now()
);