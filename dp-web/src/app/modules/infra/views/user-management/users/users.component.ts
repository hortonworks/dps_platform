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
import {UserService} from '../../../../../services/user.service';
import {User, UserList} from '../../../../../models/user';
import {TranslateService} from '@ngx-translate/core';
import {TabStyleType} from '../../../../../shared/tabs/tabs.component';

@Component({
  selector: 'dp-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})

export class UsersComponent implements OnInit {
  tabType = TabStyleType;
  tabs = UserMgmtTabs;

  users: User[] = [];
  offset = 0;
  pageSize = 10;
  total: number;
  searchTerm;
  rolesMap = new Map();

  constructor(private router: Router,
              private route: ActivatedRoute,
              private userService: UserService,
              private translateService: TranslateService) {
  }

  ngOnInit() {
    this.userService.dataChanged$.subscribe(() => {
      this.getUsers();
    });
    this.getUsers();
  }

  getUsers() {
    this.userService.getUsersWithRole(this.offset, this.pageSize, this.searchTerm).subscribe((userList: UserList) => {
      this.users = userList.users;
      this.total = userList.total;
      this.users.forEach(user => {
        let roles = [];
        user.roles.forEach(role => {
          roles.push(this.translateService.instant(`common.roles.${role}`));
        });
        this.rolesMap.set(user.id, roles.join(', '));
      });
    });
  }

  addUser() {
    this.router.navigate([{outlets: {'sidebar': ['add']}}], {relativeTo: this.route});
  }

  editUser(userName) {
    this.router.navigate([{outlets: {'sidebar': [userName, 'edit']}}], {relativeTo: this.route});
  }

  onSearch(event) {
    this.offset = 0;
    this.getUsers();
  }

  switchView(tab) {
    if(tab === UserMgmtTabs.GROUPS){
      this.router.navigate(['/infra/manage-access/groups']);
    }
  }

  get start() {
    return this.offset + 1;
  }

  onPageSizeChange(pageSize) {
    this.offset = 0;
    this.pageSize = pageSize;
    this.getUsers();
  }

  onPageChange(offset) {
    this.offset = offset - 1;
    this.getUsers();
  }

}

export enum UserMgmtTabs {
  USERS, GROUPS
}
