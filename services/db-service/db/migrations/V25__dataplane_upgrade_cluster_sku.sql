-- HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
-- (c) 2016-2018 Hortonworks, Inc. All rights reserved.
-- This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
-- Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
-- to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
-- properly licensed third party, you do not have any rights to this code.
-- If this code is provided to you under the terms of the AGPLv3:
-- (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
-- (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
-- LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
-- (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
-- FROM OR RELATED TO THE CODE; AND
-- (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
-- DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
-- DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
-- OR LOSS OR CORRUPTION OF DATA.

CREATE OR REPLACE FUNCTION dataplane.dp_services_upgrade(
  OUT o_result        INT
)
  RETURNS INT AS
$BODY$
DECLARE
  dp_cluster_ids        BIGINT [];
  service_names         VARCHAR [];
  l_cluster_id          BIGINT;
  l_dp_cluster_id       BIGINT;
  dlm_services          VARCHAR [] := '{HIVE,HDFS,BEACON}';
  dss_services          VARCHAR [] := '{ATLAS,DPPROFILER}';
  l_sku_id              BIGINT;
  l_dss_enabled_sku_id  BIGINT;
  l_dlm_enabled_sku_id  BIGINT;

BEGIN

  SELECT array_agg(id)
  INTO dp_cluster_ids
  FROM dataplane.dp_clusters;

  if dp_cluster_ids is NOT NULL THEN

    FOREACH l_dp_cluster_id in ARRAY dp_cluster_ids loop

      SELECT id INTO
      l_cluster_id
      FROM dataplane.discovered_clusters
      WHERE dp_clusterid = l_dp_cluster_id;

      SELECT array_agg(service_name)
      INTO service_names
      FROM dataplane.cluster_services
      WHERE cluster_id=l_cluster_id;

      RAISE NOTICE 'services in cluster %', service_names;
      RAISE NOTICE 'DSS Compatible %', service_names @> dss_services;
      RAISE NOTICE 'DLM Compatible %', service_names @> dlm_services;

      if service_names @> dss_services is true then

          SELECT id INTO
          l_sku_id
          from dataplane.skus
          WHERE name = 'dss';

          SELECT sku_id INTO
          l_dss_enabled_sku_id
          FROM dataplane.enabled_skus
          WHERE sku_id = l_sku_id;

          if l_dss_enabled_sku_id is NOT NULL THEN
            INSERT into
            dataplane.dp_cluster_sku(dp_cluster_id, sku_id)
            VALUES (l_dp_cluster_id, l_sku_id);
          end if;

      end if;

      if service_names @> dlm_services is true then

        SELECT id INTO
        l_sku_id
        from dataplane.skus
        WHERE name = 'dlm';

        SELECT sku_id INTO
        l_dlm_enabled_sku_id
        FROM dataplane.enabled_skus
        WHERE sku_id = l_sku_id;

        if l_dlm_enabled_sku_id is NOT NULL THEN
          INSERT into
          dataplane.dp_cluster_sku(dp_cluster_id, sku_id)
          VALUES (l_dp_cluster_id, l_sku_id);
        end if;

      end if;

    end loop;

  end if;

  o_result := 1;

END;
$BODY$
LANGUAGE 'plpgsql' VOLATILE SECURITY DEFINER
COST 100;
