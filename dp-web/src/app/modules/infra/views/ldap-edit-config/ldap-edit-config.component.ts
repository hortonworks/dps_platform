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


import { Component, OnInit } from '@angular/core';
import {LdapConfigCommonComponent} from "../../../../shared/ldap-config-common/ldap-config-common.component";
import {ConfigurationService} from "../../../../services/configuration.service";
import {ActivatedRoute, Router} from "@angular/router";
import {TranslateService} from "@ngx-translate/core";
import {Loader} from "../../../../shared/utils/loader";
import {LDAPUpdateProperties} from "../../../../models/ldap-properties";
import { SettingsService } from '../../../../services/settings.service';
import {DialogBox, DialogType} from "../../../../shared/utils/dialog-box";

@Component({
  selector: 'dp-ldap-edit-config',
  templateUrl: '../../../../shared/ldap-config-common/ldap-config-common.component.html',
  styleUrls: ['../../../../shared/ldap-config-common/ldap-config-common.component.scss']
})
export class LdapEditConfigComponent extends LdapConfigCommonComponent {

  constructor(public configurationService: ConfigurationService,
              private router: Router,
              private route: ActivatedRoute,
              public settingsService: SettingsService,
              public translateService: TranslateService) {
    super(configurationService, settingsService, translateService);
  }

  ngOnInit(){
    super.ngOnInit();
    this.isInEditMode = true;
  }
  
  showDialog(){
    DialogBox.showConfirmationMessage(
    this.translateService.instant('common.warning'),
    this.translateService.instant('pages.onboard.configure.labels.editldapWarning'),
    this.translateService.instant('common.ldapProceed'),
    this.translateService.instant('common.cancel'),
    DialogType.Confirmation
    ).subscribe(result => {
      if(result){
        this.save()
      }
    });
  }

  save() {
    const isValid = super.onSave();
    if(isValid) {
      const ldapUpdateProperties: LDAPUpdateProperties = new LDAPUpdateProperties();

      ldapUpdateProperties.id = this.ldapProperties.id;
      ldapUpdateProperties.bindDn = this.ldapProperties.useAnonymous ? '' : this.ldapProperties.bindDn;
      ldapUpdateProperties.ldapUrl = this.ldapProperties.ldapUrl;
      ldapUpdateProperties.password = this.ldapProperties.useAnonymous ? '' : this.ldapProperties.password;
      ldapUpdateProperties.useAnonymous = this.ldapProperties.useAnonymous;
      ldapUpdateProperties.referral = this.ldapProperties.referral;
      ldapUpdateProperties.userSearchBase = this.ldapProperties.userSearchBase;
      ldapUpdateProperties.userSearchAttributeName = this.ldapProperties.userSearchAttributeName;
      ldapUpdateProperties.userObjectClass = this.ldapProperties.userObjectClass;
      ldapUpdateProperties.groupSearchBase = this.ldapProperties.groupSearchBase;
      ldapUpdateProperties.groupSearchAttributeName = this.ldapProperties.groupSearchAttributeName;
      ldapUpdateProperties.groupObjectClass = this.ldapProperties.groupObjectClass;
      ldapUpdateProperties.groupMemberAttributeName = this.ldapProperties.groupMemberAttributeName;
      super.onSaveCertificate()
        .flatMap(result => this.configurationService.updateLDAP(ldapUpdateProperties))
        .subscribe(() => {
          this.router.navigate(['infra/manage-access/users', {
            status: 'success',
          }]);
          Loader.hide();
        }, response => {
          Loader.hide();
          this.showNotification = true;
          if (typeof response === 'string') {
            this.notificationMessages.push(response);
          } else if (!response || !response.error) {
            this.notificationMessages.push('Error occurred while saving the configurations.')
          } else {
            response.error
              .forEach(error => {
                this.notificationMessages.push(error.message);
              });
          }
        });  
    }
  }

}
