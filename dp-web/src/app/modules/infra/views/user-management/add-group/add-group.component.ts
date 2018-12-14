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

import {AfterViewInit, Component, HostListener, OnInit, ViewChild} from '@angular/core';
import {LDAPUser} from '../../../../../models/ldap-user';
import {UserService} from '../../../../../services/user.service';
import {ActivatedRoute, Router} from '@angular/router';
import {TaggingWidget, TaggingWidgetTagModel} from '../../../../../shared/tagging-widget/tagging-widget.component';
import {TranslateService} from '@ngx-translate/core';
import {GroupService} from '../../../../../services/group.service';
import {Group} from '../../../../../models/group';
import {NgForm} from '@angular/forms';

@Component({
  selector: 'dp-add-group',
  templateUrl: './add-group.component.html',
  styleUrls: ['./add-group.component.scss']
})
export class AddGroupComponent implements OnInit, AfterViewInit {
  groups: string[] = [];
  roles: TaggingWidgetTagModel[] = [];
  modes = Modes;
  mode = Modes.ADD;
  groupName: string;
  showRoles = false;

  availableGroups: string[] = [];
  availableRoles: TaggingWidgetTagModel[] = [];

  allRoles: TaggingWidgetTagModel[] = [];

  group: Group = new Group();
  groupRoles: TaggingWidgetTagModel[] = [];

  errorMessages: string[] = [];
  showError = false;

  @ViewChild('addGroupForm') addGroupForm: NgForm;
  @ViewChild('editGroupForm') editGroupForm: NgForm;
  @ViewChild('groupTags') private groupTags: TaggingWidget;
  @ViewChild('roleTags') private roleTags: TaggingWidget;

  groupSearchSubscription: any;

  constructor(private userService: UserService,
              private groupService: GroupService,
              private router: Router,
              private route: ActivatedRoute,
              private translateService: TranslateService) {
  }

  @HostListener('click', ['$event', '$event.target'])
  public onClick($event: MouseEvent, targetElement: HTMLElement): void {
    let optionList = targetElement.querySelector('.option-list');
    if (optionList) {
      this.showRoles = false;
    }
  }

  ngOnInit() {

    this.route.params.subscribe(params => {
      this.groupName = params['name'];
      this.groupRoles = [];
      if (this.groupName) {
        this.mode = Modes.EDIT;
        this.groupService.getGroupByName(this.groupName).subscribe(user => {
          this.group = user;
          let roles = [];
          this.group.roles.forEach(role => {
            this.groupRoles.push(new TaggingWidgetTagModel(this.translateService.instant(`common.roles.${role}`), role));
          });
        });
      }
    });
    this.userService.getAllRoles().subscribe(roles => {
      this.allRoles = roles.map(role => {
        return new TaggingWidgetTagModel(this.translateService.instant(`common.roles.${role.roleName}`), role.roleName);
      })
    });
  }

  ngAfterViewInit() {
    setTimeout(() => {
      let element: any = this.mode as Modes === Modes.EDIT ?
        document.querySelector('#role-tags').querySelector('.taggingWidget') :
        document.querySelector('#group-tags').querySelector('.taggingWidget');
      element.click();
    }, 500);
  }

  onNewGroupAddition(text: string) {
    if (this.groups.find(usr => usr === text)) {
      this.showWarning(`${this.translateService.instant('pages.infra.labels.duplicateGroup')}${text}`, document.getElementById('duplicate-group-warning'));
      return;
    }
    this.groups.push(text);
  }

  onGroupSearchChange(text: string) {
    if (this.groupSearchSubscription) {
      this.groupSearchSubscription.unsubscribe();
    }
    if (text) {
      this.groupSearchSubscription = this.userService.searchLDAPGroups(text).subscribe((ldapUsers: LDAPUser[]) => {
        this.availableGroups = ldapUsers.map(user => user.name);
      }, () => {
        this.showError = true;
        this.onError(this.translateService.instant('pages.infra.description.ldapError'));
      });
    } else {
      this.availableGroups = [];
    }
  }

  onNewRoleAddition(tag: TaggingWidgetTagModel) {
    if (this.roles.find(role => role.data === tag.data)) {
      this.showWarning(`${this.translateService.instant('pages.infra.labels.duplicateRole')}${tag.display}`, document.getElementById('duplicate-role-warning'));
      return;
    }
    this.roles.push(tag);
  }

  onRolesEdit(tag: TaggingWidgetTagModel) {
    if (this.groupRoles.find(role => role.data === tag.data)) {
      this.showWarning(`${this.translateService.instant('pages.infra.labels.duplicateRole')}${tag.display}`, document.getElementById('duplicate-role-warning'));
      return;
    }
    this.groupRoles.push(tag);
  }

  private showWarning(message, element) {
    element.innerHTML = message;
    element.style.display = 'block';
    element.style.opacity = 1;
    setTimeout(() => {
      let opacity = 1;
      let fade = setInterval(() => {
        opacity -= 0.3;
        element.style.opacity = opacity;
        if (opacity <= 0) {
          clearInterval(fade);
          element.style.display = 'none';
        }
      }, 100);
    }, 1000);
  }

  onRoleSearchChange(text: string) {
    if (text) {
      this.availableRoles = this.allRoles.filter(role => {
        return role.display.toLowerCase().startsWith(text.toLowerCase());
      });
    } else {
      this.availableRoles = [];
    }
  }

  showRoleOptions() {
    this.showRoles = !this.showRoles;
  }

  save() {
    this.clearErrors();
    if (this.mode as Modes === Modes.EDIT && this.isEditDataValid()) {
      this.group.roles = this.groupRoles.map(role => {
        return role.data
      });
      this.groupService.updateGroup(this.group).subscribe(user => {
        this.groupService.dataChanged.next();
        this.router.navigate(['groups'], {relativeTo: this.route});
      }, error => {
        this.onError(this.translateService.instant('pages.infra.description.updateGroupError'));
      });
    } else if (this.mode as Modes === Modes.ADD && this.isCreateDataValid()) {
      let roles = this.roles.map(role => {
        return role.data;
      });
      this.groupService.addGroups(this.groups, roles).subscribe(response => {
        if (response.successfullyAdded.length === this.groups.length) {
          this.groupService.dataChanged.next();
          this.router.navigate(['groups'], {relativeTo: this.route});
        } else {
          let failedGroups = [];
          this.groups.forEach(grp => {
            if (!response.successfullyAdded.find(res => res.groupName === grp)) {
              failedGroups.push(grp);
            }
          });
          this.groupService.dataChanged.next();
          this.onError(`${this.translateService.instant('pages.infra.description.addGroupError')} - ${failedGroups.join(', ')}`);
        }
      }, error => {
        this.onError(this.translateService.instant('pages.infra.description.addGroupError'));
      });
    }
  }


  clearErrors() {
    this.showError = false;
    this.errorMessages = []
  }

  isEditDataValid() {
    let valid = true;
    if (this.groupRoles.length === 0 || !this.editGroupForm.form.valid) {
      this.onError(this.translateService.instant('common.defaultRequiredFields'));
      valid = false;
    } else if (!this.roleTags.isValid) {
      this.onError(this.translateService.instant('pages.infra.description.invalidRoleInput'));
      valid = false;
    }
    return valid;
  }

  isCreateDataValid() {
    let valid = true;
    if (this.roles.length === 0 || !this.addGroupForm.form.valid) {
      this.onError(this.translateService.instant('common.defaultRequiredFields'));
      valid = false;
    }
    if (!this.roleTags.isValid) {
      this.onError(this.translateService.instant('pages.infra.description.invalidRoleInput'));
      valid = false;
    }
    if (!this.groupTags.isValid) {
      this.onError(this.translateService.instant('pages.infra.description.invalidGroupInput'));
      valid = false;
    }
    return valid;
  }

  onError(errorMessage) {
    this.errorMessages.push(errorMessage);
    this.showError = true;
  }
}

export enum Modes {
  ADD,
  EDIT
}
