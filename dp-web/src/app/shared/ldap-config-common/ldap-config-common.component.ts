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
import {Observable} from 'rxjs/Observable';
import {LDAPProperties} from "../../models/ldap-properties";
import {NgForm} from "@angular/forms";
import {ConfigurationService} from "../../services/configuration.service";
import {Loader} from "../utils/loader";
import {TranslateService} from "@ngx-translate/core";
import { SettingsService } from '../../services/settings.service';
import { DialogBox } from '../utils/dialog-box';
import { Certificate } from '../../models/certificate';

const LDAP_CERT_NAME = 'ldap-cert';

export class LdapConfigCommonComponent implements OnInit {

  showKnoxPassword = false;
  showLdapPassword = false;
  showNotification = false;
  notificationMessages: string[] = [];
  ldapProperties: LDAPProperties = new LDAPProperties();
  isInEditMode: boolean = false;
  showCertInput: boolean = false;

  cert: any = null;
  file: { name: string, data: string } = { name: '', data: '' };

  @ViewChild('configForm') configForm: NgForm;
  @ViewChild('fileInput') fileInput;

  constructor(
    public configurationService: ConfigurationService,
    public settingsService: SettingsService,
    public translateService: TranslateService
  ) {}

  ngOnInit() {
    Loader.show();
    Observable.forkJoin(
      this.settingsService.listCerts({ name: LDAP_CERT_NAME }),
      this.configurationService.getLdapConfiguration(),
      (certs, config) => ({ certs, config })
    )
    .subscribe(({certs, config}) => {
      this.ldapProperties = config;
      if(certs.length > 0) {
        this.cert = certs[0];
      }
      this.setDefaultObjectClassValue();
      Loader.hide();
    }, error => {
      Loader.hide();
    });

    let currentLocation = window.location.href.split('/');
    let domain = currentLocation[2].indexOf(':') > -1 ? currentLocation[2].substring(0, currentLocation[2].indexOf(':')) : currentLocation[2];
    if(!this.ldapProperties.domains.find(dm => dm === domain)){
      this.ldapProperties.domains.push(domain);
    }
  }

  setDefaultObjectClassValue(){
    if(this.ldapProperties && !this.ldapProperties.userObjectClass){
      this.ldapProperties.userObjectClass = 'person';
    }
  }

  onSave(): boolean {
    this.notificationMessages = [];
    if (!this.configForm.form.valid) {
      this.translateService.get('common.defaultRequiredFields')
        .subscribe(msg => this.notificationMessages.push(msg));
      this.showNotification = true;
      return false;
    }
    Loader.show();
    return true;
  }

  closeNotification() {
    this.showNotification = false;
  }

  onSaveCertificate(): Observable<string> {
    const file = '';
    if(this.file.name && this.file.data) {
      const rxCert = this.cert && this.cert.id
        ? this.settingsService.updateCert(Certificate.from({id: this.cert.id , name: LDAP_CERT_NAME, data: this.file.data}))
        : this.settingsService.uploadCert(Certificate.from({name: LDAP_CERT_NAME, data: this.file.data}));
      return rxCert
        .map(cert => {
          this.cert = cert;
          return cert;
        })
        .catch(error => Observable.throw(this.translateService.instant('pages.onboard.configure.description.genericCertificateError')));
    } else {
      return Observable.of('No certs available.');
    }
  }

  onFileChanged(files) {
    read(files[0])
      .then(data => this.file = data)
      .catch(err => {
        // do nothing
      });
  }

  onShowCertificate() {
    DialogBox.showConfirmationMessage(
      this.translateService.instant('pages.onboard.configure.labels.certificateDialogTitle'),
      `<pre>${this.cert.data}</pre>`,
      this.translateService.instant('common.ok')
    );
  }

}


function read(file): Promise<{ name: string, data: string }> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = data => resolve({
      name: file.name,
      data: reader.result
    });
    reader.onerror = (event: any) => reject(event.target.error);
    reader.readAsText(file);
  });
}
