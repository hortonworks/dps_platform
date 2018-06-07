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
CREATE OR REPLACE FUNCTION dataplane.dp_cluster_delete(
  IN  p_dp_cluster_id BIGINT,
  OUT o_result        INT
)
  RETURNS INT AS

-- Takes a dp cluster
-- and cleans up the DB in one transaction
-- A failure here will show up as an SQL exception
-- and get mapped into a ISE, or this routine will succeed and return 1
$BODY$
DECLARE

  l_cluster_id         BIGINT;
  l_orphaned_catgories BIGINT [];
  l_dataset_ids        BIGINT [];
BEGIN

  -- get discovered clusters
  SELECT id
  INTO l_cluster_id
  FROM dataplane.discovered_clusters
  WHERE dp_clusterid = p_dp_cluster_id;

  -- Delete service hosts
  DELETE FROM dataplane.cluster_service_hosts
  USING dataplane.cluster_services CS
  WHERE service_id = CS.id
        AND CS.cluster_id = l_cluster_id;

  -- Delete cluster related data
  DELETE FROM dataplane.cluster_hosts
  WHERE cluster_id = l_cluster_id;
  DELETE FROM dataplane.cluster_services
  WHERE cluster_id = l_cluster_id;
  DELETE FROM dataplane.cluster_properties
  WHERE cluster_id = l_cluster_id;

  -- Fetch affected data sets
  SELECT array_agg(id)
  INTO l_dataset_ids
  FROM dataplane.datasets
  WHERE dp_clusterid = p_dp_cluster_id;

  -- Delete data assets for all affected data sets
  DELETE FROM dataplane.data_asset
  WHERE dataset_id = ANY (l_dataset_ids);

  -- Delete category mappings
  DELETE FROM dataplane.dataset_categories
  WHERE dataset_id = ANY (l_dataset_ids);

  -- Delete any orphaned categories
  SELECT array_agg(id)
  INTO l_orphaned_catgories
  FROM dataplane.categories C LEFT JOIN dataplane.dataset_categories DC
      ON C.id = DC.category_id
         AND DC.category_id IS NULL;

  DELETE FROM dataplane.categories
  WHERE id = ANY (l_orphaned_catgories);

  -- delete data sets
  DELETE FROM dataplane.datasets
  WHERE id = ANY (l_dataset_ids);

  -- Delete mapped cluster
  DELETE FROM dataplane.discovered_clusters
  WHERE dp_clusterid = p_dp_cluster_id;

  -- Delete the dataplane cluster mapping
  DELETE FROM dataplane.dp_clusters
  WHERE id = p_dp_cluster_id;

  -- Send a result to make slick happy
  o_result := 1;

END;
$BODY$
LANGUAGE 'plpgsql' VOLATILE SECURITY DEFINER
COST 100;