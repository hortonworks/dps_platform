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

import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {GroupService} from '../../../../../services/group.service';
import {TranslateService} from '@ngx-translate/core';
import {Group, GroupList} from '../../../../../models/group';
import {TabStyleType} from '../../../../../shared/tabs/tabs.component';
@Component({
  selector: 'dp-groups',
  templateUrl: './groups.component.html',
  styleUrls: ['./groups.component.scss']
})
export class GroupsComponent implements OnInit {

  tabType = TabStyleType;
  tabs = UserMgmtTabs;

  groups: Group[] = [];
  offset = 0;
  pageSize = 10;
  total: number;
  searchTerm;
  rolesMap = new Map();

  constructor(private router: Router,
              private route: ActivatedRoute,
              private groupService: GroupService,
              private translateService: TranslateService) {
  }

  ngOnInit() {
    this.groupService.dataChanged$.subscribe(() => {
      this.getGroups();
    });
    this.getGroups();
  }

  getGroups() {
    this.groupService.getAllGroups(this.offset, this.pageSize, this.searchTerm).subscribe((groupList: GroupList) => {
      this.groups = groupList.groups;
      this.total = groupList.total;
      this.groups.forEach(group => {
        let roles = [];
        group.roles.forEach(role => {
          roles.push(this.translateService.instant(`common.roles.${role}`));
        });
        this.rolesMap.set(group.id, roles.join(', '));
      });
    });
  }

  addGroup() {
    this.router.navigate([{outlets: {'sidebar': ['add']}}], {relativeTo: this.route});
  }

  editGroup(groupName) {
    this.router.navigate([{outlets: {'sidebar': [groupName, 'edit']}}], {relativeTo: this.route});
  }

  onSearch(event) {
    this.offset = 0;
    this.getGroups();
  }

  switchView(tab) {
    if (tab === UserMgmtTabs.USERS) {
      this.router.navigate(['/infra/manage-access/users']);
    }
  }

  get start() {
    return this.offset + 1;
  }

  onPageSizeChange(pageSize) {
    this.offset = 0;
    this.pageSize = pageSize;
    this.getGroups();
  }

  onPageChange(offset) {
    this.offset = offset - 1;
    this.getGroups();
  }

}

export enum UserMgmtTabs {
  USERS, GROUPS
}
