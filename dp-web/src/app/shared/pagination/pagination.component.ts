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

import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from '@angular/core';

@Component({
  selector: 'simple-pagination',
  templateUrl: './pagination.component.html',
  styleUrls: ['./pagination.component.scss']
})
export class SimplePaginationWidget {
  infinity: number = Infinity;
  @Input() pageSize: number;
  @Input() pageSizeOptions: number[] = [10, 20, 50, 100, 200, 500];
  @Input() pageStartIndex: number;
  @Input() count: number;
  @Output('onPageChange') indexEmitter: EventEmitter<number> = new EventEmitter<number>();
  @Output('onPageSizeChange') pageSizeEmitter: EventEmitter<number> = new EventEmitter<number>();

  get start(): number {
    return Math.min(this.count, this.pageStartIndex);
  }

  get end(): number {
    return Math.min(this.count, this.pageSize + this.pageStartIndex - 1);
  }

  get showPagination(): boolean {
    return this.count > this.pageSizeOptions[0];
  }

  pageSizeChange() {
    this.pageSizeEmitter.emit(this.pageSize = +this.pageSize);
  }

  previous() {
    if (this.start > 1) {
      this.pageStartIndex = this.pageStartIndex - this.pageSize;
      this.indexEmitter.emit(this.pageStartIndex);
    }
  }

  next() {
    if (this.end !== this.count) {
      this.pageStartIndex = this.pageStartIndex + this.pageSize;
      this.indexEmitter.emit(this.pageStartIndex);
    }
  }
}
