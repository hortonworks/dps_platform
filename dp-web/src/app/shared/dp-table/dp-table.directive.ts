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

import {Directive, Output, Input, EventEmitter, ElementRef, AfterViewInit} from '@angular/core';
import {Sort} from '../utils/enums';

export interface SortEvent {
  sortBy: string;
  type: string;
  sortOrder: Sort;
}

@Directive({
  selector: '[dp-config-table]'
})

export class DpTableDirective {

  @Output() onSort = new EventEmitter<SortEvent>();
  @Input() data: any[] = [];
  @Input() cellSelectable = false;
  rowhighlightColor = '#333333';
  highlightColor = '#0F4450';
  border = '1px solid #1B596C';

  onSortColumnChange = new EventEmitter<SortEvent>();

  constructor(private element: ElementRef) { }


  public setSort(sortEvent: SortEvent): void {
    this.onSortColumnChange.emit(sortEvent);
    if (this.onSort.observers.length === 0 ) {
      this.sort(sortEvent);
    } else {
      this.onSort.emit(sortEvent);
    }
  }

  private sort($event) {
    this.data.sort((obj1: any, obj2: any) => {
      if ($event.sortOrder === Sort.ASC) {
        if ($event.type === 'string') {
          return obj1[$event.sortBy].localeCompare(obj2[$event.sortBy]);
        }
        if ($event.type === 'number') {
          return obj1[$event.sortBy] - obj2[$event.sortBy];
        }
      }

      if ($event.type === 'string') {
        return obj2[$event.sortBy].localeCompare(obj1[$event.sortBy]);
      }
      if ($event.type === 'number') {
        return obj2[$event.sortBy] - obj1[$event.sortBy];
      }
    });
  }
}
