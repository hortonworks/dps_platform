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

import {Component, OnInit, ViewChild} from '@angular/core';
import {Router} from '@angular/router';
import {UserService} from '../../../../services/user.service';
import {LDAPUser} from '../../../../models/ldap-user';
import {TaggingWidget} from '../../../../shared/tagging-widget/tagging-widget.component';
import {AuthenticationService} from '../../../../services/authentication.service';
import {Observable} from 'rxjs/Observable';
import {GroupService} from '../../../../services/group.service';
import {TranslateService} from '@ngx-translate/core';
import {Loader} from "../../../../shared/utils/loader";

@Component({
  selector: 'dp-user-add',
  templateUrl: './user-add.component.html',
  styleUrls: ['./user-add.component.scss', '../dp-onboard/dp-onboard.component.scss']
})
export class UserAddComponent implements OnInit {

  showNotification = false;
  users: string[] = [];
  groups: string[] = [];
  availableUsers: string[] = [];
  availableGroups: string[] = [];
  groupsSaved = false;
  usersSaved = false;

  errorMessage: string = '';
  showError = false;

  @ViewChild('userTags') private userTags: TaggingWidget;
  @ViewChild('groupTags') private groupTags: TaggingWidget;

  userSearchSubscription: any;
  groupSearchSubscription: any;


  constructor(private router: Router,
              private userService: UserService,
              private groupService: GroupService,
              private authenticationService: AuthenticationService,
              private translateService: TranslateService) {
  }

  ngOnInit() {
    this.showNotification = true;
  }

  ngAfterViewInit() {
    setTimeout(() => {
      let element: any = document.querySelector('#users-tags').querySelector('.taggingWidget');
      element.click();
    }, 500);
  }

  closeNotification() {
    this.showNotification = false;
  }

  save() {
    if (!this.userTags.isValid) {
      this.onError(this.translateService.instant('pages.infra.description.invalidUserInput'));
      return;
    }
    if (!this.groupTags.isValid) {
      this.onError(this.translateService.instant('pages.infra.description.invalidGroupInput'));
      return;
    }
    if (!this.groupsSaved && !this.usersSaved) {
      this.saveUsersAndGroups();
    } else if (!this.usersSaved && this.groupsSaved) {
      this.saveUsers();
    } else if (this.usersSaved && !this.groupsSaved) {
      this.saveGroups();
    }
  }

  saveUsers() {
    this.userService.addAdminUsers(this.users).subscribe(response => {
      if (response.successfullyAdded.length === this.users.length) {
        this.usersSaved = true;
        this.signOut();
      }
      let failedUsers = [];
      this.users.forEach(user => {
        if (!response.successfullyAdded.find(res => res.userName === user)) {
          failedUsers.push(user)
        }
      });
      this.onError(`${this.translateService.instant('pages.infra.description.addUserError')} - ${failedUsers.join(', ')}`);
    }, (error) => {
      this.onError(`${this.translateService.instant('pages.infra.description.addUserError')}`);
      this.usersSaved = false;
    });
  }

  saveGroups() {
    this.groupService.addAdminGroups(this.groups).subscribe(response => {
      if (response.successfullyAdded.length === this.groups.length) {
        this.groupsSaved = true;
        this.signOut();
      }
      let failedGroups = [];
      this.groups.forEach(grp => {
        if (!response.successfullyAdded.find(res => res.groupName === grp)) {
          failedGroups.push(grp);
        }
      });
      this.onError(`${this.translateService.instant('pages.infra.description.addGroupError')} - ${failedGroups.join(', ')}`);
    }, (error) => {
      this.onError(`${this.translateService.instant('pages.infra.description.addGroupError')}`);
      this.groupsSaved = false;
    });
  }

  saveUsersAndGroups() {
    return Observable.forkJoin(
      this.userService.addAdminUsers(this.users),
      this.groupService.addAdminGroups(this.groups)
    ).subscribe(responses => {
      this.usersSaved = this.users.length === responses[0].successfullyAdded.length;
      this.groupsSaved = this.groups.length === responses[1].successfullyAdded.length;
      if (this.usersSaved && this.groupsSaved) {
        this.signOut();
      }
      if (!this.usersSaved) {
        this.onError(`${this.translateService.instant('pages.infra.description.addUserError')}`);
      }
      if (!this.groupsSaved) {
        this.onError(`${this.translateService.instant('pages.infra.description.addGroupError')}`);
      }
    });
  }

  signOut(){
    let count = 1;
    Loader.show();
    this.authenticationService.signOut().subscribe(redirectUrl => {
      let poll = setInterval(() => {
        count++;
        this.authenticationService.getAuthServerStatus(redirectUrl).subscribe(response => {
          if(response.status === 200){
            clearInterval(poll);
            Loader.hide();
            const redirectTo = `${window.location.protocol}//${window.location.host}/${redirectUrl}`;
            window.location.href = `${redirectTo}?originalUrl=${window.location.protocol}//${window.location.host}/`;
          }else if(count > 20){
            clearInterval(poll);
            Loader.hide();
            this.onError("Could not redirect to Login page.")
          }
        })
      }, 1000);
    });
  }

  onNewUserAddition(text: string) {
    this.users.push(text);
  }

  onNewGroupAddition(text: string) {
    this.groups.push(text);
  }

  onUserSearchChange(text: string) {
    this.hideError();
    if (this.userSearchSubscription) {
      this.userSearchSubscription.unsubscribe();
    }
    if (text) {
      this.userSearchSubscription = this.userService.searchLDAPUsers(text).subscribe((ldapUsers: LDAPUser[]) => {
        this.availableUsers = ldapUsers.map(user => user.name);
      }, () => {
        this.onError(this.translateService.instant('pages.onboard.adduser.description.ldapUserFetchError'));
      });
    } else {
      this.availableUsers = [];
    }
  }

  onGroupSearchChange(text: string) {
    this.hideError();
    if (this.groupSearchSubscription) {
      this.groupSearchSubscription.unsubscribe();
    }
    if (text) {
      this.groupSearchSubscription = this.userService.searchLDAPGroups(text).subscribe((ldapGroups: any[]) => {
        this.availableGroups = ldapGroups.map(group => group.name);
      }, () => {
        this.onError(this.translateService.instant('pages.onboard.adduser.description.ldapUserFetchError'));
      });
    } else {
      this.availableGroups = [];
    }
  }

  onError(errorMessage: string) {
    this.showError = true;
    this.errorMessage = errorMessage;
  }

  hideError() {
    this.showError = false;
  }

}
