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
-- Add your indexes here

--Users
CREATE INDEX idx_dp_user_username on dataplane.users(user_name);
CREATE INDEX idx_dp_user_displayname on dataplane.users(display_name);

--Roles
CREATE INDEX idx_dp_roles_name on dataplane.roles(name);

-- Configs
CREATE INDEX idx_dp_configs on dataplane.configs(config_key);

-- Locations
CREATE INDEX idx_dp_locations on dataplane.locations(city);

-- cluster services
 CREATE INDEX idx_dp_clusterservices_servicename on dataplane.cluster_services(service_name);

-- workspaces
 CREATE INDEX idx_dp_workspace_name on dataplane.workspace(name);

-- datasets
 CREATE INDEX idx_dp_dataset_active on dataplane.datasets(id) WHERE active;

 -- data-assets
 CREATE INDEX idx_dp_data_asset_guid on dataplane.data_asset(guid);

 --user-groups
 CREATE INDEX idx_dp_user_groups_user_id on dataplane.user_groups(user_id);
 CREATE INDEX idx_dp_user_groups_group_id on dataplane.user_groups(group_id);
