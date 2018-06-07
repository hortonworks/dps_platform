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

import {Component, ViewChild, Output, EventEmitter} from '@angular/core';
import {SettingsService} from "../../services/settings.service";
import {NgForm} from "@angular/forms";
import {TranslateService} from "@ngx-translate/core";
import { Certificate } from '../../models/certificate';

@Component({
  selector: 'dp-cert-upload',
  templateUrl: './cert-upload.component.html',
  styleUrls: ['./cert-upload.component.scss']
})
export class CertUploadComponent {
  uploadSuccess = false;
  uploadFailure = false;
  uploading = false;
  showError = false;
  errorMessage: string;

  selectedFile: String;
  name: string;
  @ViewChild('fileInput') fileInput;
  @ViewChild('uploadForm') uploadForm: NgForm;
  @Output('onUploadComplete') onUploadComplete: EventEmitter<any> = new EventEmitter();
  @Output('onUploadCancel') onUploadCancel: EventEmitter<any> = new EventEmitter();

  constructor(private settingsService: SettingsService, private translateService: TranslateService) { }

  clearForm(){
    this.selectedFile = '';
    this.name = '';
    this.uploading = false;
    this.showError = false;
    this.uploadFailure = false;
    this.uploadSuccess = false;
  }

  fileChanged(files){
    this.selectedFile = files[0].name;
  }

  upload(): void {
    let files = this.fileInput.nativeElement.files;
    if(!this.uploadForm.valid || !files || !files.length){
      this.showError = true;
      this.errorMessage =  this.translateService.instant("pages.settings.description.validationErrorMessage");//'Please fill all the fields marked with (*)!';
      return;
    }
    this.uploading = true;
    let file = files[0];
    let reader = new FileReader();
    reader.readAsText(file);
    reader.onload = (data) => {
      let result = reader.result;
      this.settingsService.uploadCert(Certificate.from({name: this.name, data: result})).subscribe(response =>{
        this.onUploadComplete.emit();
        this.clearForm();
        this.uploading = false;
        this.uploadSuccess = true;
        this.uploadFailure = false;
      },(error)=>{
        this.uploading = false;
        this.uploadSuccess = false;
        this.uploadFailure = true;
      })
    };
    reader.onerror = (event: any) => {
      console.error(event.target.error);
      this.uploading = false;
      this.errorMessage = event.target.error || this.translateService.instant("pages.settings.description.fileReadErrorMessage");
      this.showError = true;
    };
  }

  onCancel() {
    this.clearForm();
    this.onUploadCancel.emit();
  }

}
