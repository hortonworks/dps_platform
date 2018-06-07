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

import {Component} from '@angular/core';
import {Router} from '@angular/router';
import {FormGroup, FormControl, Validators, ValidatorFn} from '@angular/forms';
import {Observable} from 'rxjs/Rx';

import {IdentityService} from '../../../../services/identity.service';
import {AuthenticationService} from '../../../../services/authentication.service';

@Component({
  selector: 'dp-change-password',
  templateUrl: './change-password.component.html',
  styleUrls: ['./change-password.component.scss']
})
export class ChangePasswordComponent {

  form: FormGroup;

  passwordCurrent: string;
  passwordNew: string;
  passwordNewConfirm: string;

  serverError: string;

  constructor(
    private router: Router,
    private identityService: IdentityService,
    private authenticationService: AuthenticationService
  ) {
    this.form = new FormGroup({
        'passwordCurrent': new FormControl(this.passwordCurrent, [
          Validators.required,
        ]),
        'passwordNew': new FormControl(this.passwordNew, [
          Validators.required,
        ]),
        'passwordNewConfirm': new FormControl(this.passwordNewConfirm, [
          Validators.required,
        ])
      },
      (group: FormGroup) => {
        if(group.controls['passwordNew'].value !== group.controls['passwordNewConfirm'].value) {
          return ({ noMatch : true });
        }
      }
    );
  }

  onSubmit() {
    if(this.form.valid === false) {
      // do nothing
    } else {
      this.identityService
        .changePassword(this.passwordCurrent, this.passwordNew)
        .subscribe(
          () => {
            this.authenticationService
              .signOutAndRedirect();
          },
          error => {
            if (error._body) {
              let errorJSON = JSON.parse(error._body);
              if(Array.isArray(errorJSON.errors) && errorJSON.errors[0] && errorJSON.errors[0].status && errorJSON.errors[0].errorType){
                this.serverError = errorJSON.errors.filter(err => {return (err.status && err.errorType)}).map(err => {return err.message}).join(', ');
              } else if(errorJSON.message){
                this.serverError = errorJSON.message
              } else if (errorJSON.errors){
                this.serverError = errorJSON.errors.map(err => {return err.message}).join(', ')
              } else {
                this.serverError = error;
              }
            }
          }
        );
    }
  }

}
