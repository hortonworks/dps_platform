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

/*
* Usage :
* <multi-select [list]="myList" (update)="onMultiSelectChange($event)"></multi-select>
*
*
*class consumerComponent{
*   myList: any[] = [{"name":"display name"}, ...];
*   onMultiSelectChange(selection : any[]) {
*     // do something with selection
*   }
* }
*
* */
import {Component, Input, Output, EventEmitter} from "@angular/core";

@Component({
  selector: 'multi-select',
  template: `
    <div *ngFor="let optn of options; let i = index">
      <input #checkboxInput type="checkbox" [checked]="optn.checked" (click)="updateChecked(i, checkboxInput.checked)"/>
      <label (click)="updateChecked(i, checkboxInput.checked = !checkboxInput.checked);">{{optn.name}}</label>
    </div>
  `
})
export class MultiSelect {
  @Input('list')
  options : any[];

  @Output('update')
  changeEmitter: EventEmitter<any[]> = new EventEmitter<any[]>();

  updateChecked (i, state) {
    this.options[i].checked = state;
    this.changeEmitter.emit(this.options)
  }
}
