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

import {Injectable} from "@angular/core";
import {Http, RequestOptions} from "@angular/http";
import {Observable} from "rxjs";
import {HttpUtil} from "../shared/utils/httpUtil";
import {DatasetTag} from "../models/dataset-tag";

@Injectable()
export class DatasetTagService {
  url = 'api/dataset-tag/list';

  constructor(private http: Http) {
  }

  public list(text:string): Observable<DatasetTag[]> {
    return this.http
      .get(`${this.url}?search=${text}`, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(HttpUtil.handleError);

    // return Observable.create(observer => {
    //   setTimeout(()=>observer.next(data), 300);
    // });

  }
}
// Tags must have unique name.
var data = [{"name":"All", "count":895}
  ,  {"name":"Favourites", "count":53}
  ,  {"name":"Dataset01", "count":5}
  ,  {"name":"Classified", "count":15}
  ,  {"name":"Sales", "count":10}
  ,  {"name":"Marketing", "count":7}
  ,  {"name":"Finance", "count":22}
  ,  {"name":"HRD", "count":50}
  ,  {"name":"My Datasets", "count":201}
  ,  {"name":"Block", "count":53}
  ,  {"name":"Bla", "count":45}
  ,  {"name":"Awesome", "count":12}
  ,  {"name":"Recent", "count":342}
  ,  {"name":"Old", "count":23}

];
