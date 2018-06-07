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
ALTER TABLE dataplane.datasets ADD sharedstatus SMALLINT DEFAULT 1;   -- enum 1 - Public, 2 - Private

CREATE TABLE IF NOT EXISTS dataplane.comments (
  id           BIGSERIAL PRIMARY KEY,
  comment      TEXT,
  object_type  VARCHAR(255)                                           NOT NULL,
  object_id    BIGINT                                                 NOT NULL,
  createdby    BIGINT REFERENCES dataplane.users (id)                 NOT NULL,
  createdon    TIMESTAMP DEFAULT now(),
  lastmodified TIMESTAMP DEFAULT now(),
  parent_comment_id  BIGINT REFERENCES dataplane.comments(id)         ON DELETE CASCADE DEFAULT NULL,
  number_of_replies BIGINT DEFAULT 0,
  edit_version BIGINT DEFAULT 0
);

--Index on comments table
CREATE INDEX idx_dp_comments_parent_id on dataplane.comments(parent_comment_id);

CREATE TABLE IF NOT EXISTS dataplane.ratings (
  id           BIGSERIAL PRIMARY KEY,
  rating       DECIMAL(2,1)                                           NOT NULL,
  object_type  VARCHAR(255)                                           NOT NULL,
  object_id    BIGINT                                                 NOT NULL,
  createdby    BIGINT REFERENCES dataplane.users (id)                 NOT NULL,

  CONSTRAINT unique_creator_objId_objType_constraint UNIQUE (createdby, object_id,object_type)
);

CREATE TABLE IF NOT EXISTS dataplane.favourites (
  id           BIGSERIAL PRIMARY KEY,
  user_id    BIGINT REFERENCES dataplane.users (id)                 NOT NULL,
  object_type  VARCHAR(255)                                         NOT NULL,
  object_id  BIGINT                                       NOT NULL,

  CONSTRAINT fav_unique_userId_objId_objType_constraint UNIQUE (user_id, object_id,object_type)
);

--Index on favourites table
CREATE INDEX idx_dp_favourites_user_id on dataplane.favourites(user_id);
CREATE INDEX idx_dp_favourites_objId_objType on dataplane.favourites(object_id, object_type);

CREATE TABLE IF NOT EXISTS dataplane.bookmarks (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT REFERENCES dataplane.users (id)                 NOT NULL,
  object_type  VARCHAR(255)                                         NOT NULL,
  object_id  BIGINT                                       NOT NULL,

  CONSTRAINT bm_unique_userId_objId_objType_constraint UNIQUE (user_id, object_id,object_type)
);

--Index on bookmarks table
CREATE INDEX idx_dp_bookmarks_user_id on dataplane.bookmarks(user_id);
CREATE INDEX idx_dp_bookmarks_objId_objType on dataplane.bookmarks(object_id, object_type);

CREATE TABLE IF NOT EXISTS dataplane.dataset_edit_details (
  id           BIGSERIAL PRIMARY KEY,
  dataset_id   BIGINT REFERENCES dataplane.datasets (id)              NOT NULL,
  edited_by    BIGINT REFERENCES dataplane.users (id)                 NOT NULL,
  edit_begin   TIMESTAMP DEFAULT now()                                NOT NULL
);
