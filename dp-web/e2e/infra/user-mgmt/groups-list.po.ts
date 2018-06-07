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

import {browser, $, element, by, $$} from 'protractor';
import {helper} from '../../utils/helpers';

export class GroupsListPage {
  cGroupsTable = $('[data-se="groups__groups-table" ]');
  cGroupsList = $$('[data-se-group="groups__groups-list"]');
  cTabs = $('[data-se="groups__user-group-tab"]');
  bAddgroup = $('[data-se="groups__addGroupButton"]');
  cAddgroupSlider = $('[data-se="groups__slider-add-group"]');
  cGroupsInputContainer = $('[data-se="groups__add-group__groups-input"]');
  cRolesInputContainer = $('[data-se="groups__add-group__roles-input"]');
  bSaveButton = $('[data-se="groups__add-group__save"]');
  bCancelButton = $('[data-se="groups__add-group__cancel"]');
  cErrorNotification = $('[data-se="groups__add-group__error"]');

  async get() {
    await helper.safeGet('/infra/manage-access/groups');
  }

  getGroupname(container) {
    return container.$('[data-se="groups__groups-list__groupname"]');
  }

  getMenu(container) {
    return container.$('[data-se="groups__groups-list__row-menu"]');
  }

  getMenuItems(container) {
    return container.$$('[data-se="groups__groups-list__row-menu-items"]');
  }

  getGroupsTab(element) {
    return element.$('[data-se="tab_1"]');
  }

  getInputElement(container) {
    return container.$('[data-se="common__taggingWidget__tagInput"]');
  }

  getDropDown(container) {
    return container.$('[data-se="common__tagging-widget__tags-container"]');
  }

  getTagOptions(container) {
    return container.$$('[data-se-group="common__tagging-widget__tag-options"]');
  }

  getTags(container) {
    return container.$$('[data-se-group="common__tagging-widget__tags"]');
  }
}
