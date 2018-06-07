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

export class StringUtils {
  public static trunc(str: string, n: number) {
    return (str.length > n) ? str.substr(0, n - 1) + '...' : str;
  };

  public static cleanupUri(url: string): string {
    if (!url || url.length === 0) {
      return '';
    }
    url = url.replace(/^\s+|\s+$/g, '');
    url = url.replace(/\/$/, '');

    let protoHostArray = url.split("://");
    if(protoHostArray.length < 2 || !(protoHostArray[0] === "http" || protoHostArray[0] === "https")){
      return '';
    }

    let link = document.createElement('a');
    link.setAttribute('href', url);

    let urlHostInfo = protoHostArray[1].split("/")[0];
    if(!!link.port){ // !! => spaces
      urlHostInfo = protoHostArray[1].split(":")[0];
    }

    if(urlHostInfo !== link.hostname){
      return '';
    }

    let port = (!link.port ? '': ":"+link.port);
    let pathname = (link.pathname === "/") ? '':link.pathname;

    const cleanedUri = `${link.protocol || 'http:'}//${link.hostname + port + pathname}`;
    // cleanup for garbage collection
    // prevent leaks
    link = null;
    return cleanedUri;
  }

  public static humanizeBytes(bytes: number): string {
    if (bytes == 0) {
      return '0 Bytes';
    }
    let sizes = Array('Bytes ', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB' , 'ZB', 'YB')
    let k = 1024;
    let i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i)) + ' ' + sizes[i];
  }
  public static getFlattenedObjects(obj: any): string {
    let objArray = Object.keys(obj).map((key) => {
      if (!obj[key]) {
        return;
      }
      if (!Array.isArray(obj[key]) && typeof obj[key] !== 'object') {
        return `${key}: ${obj[key]}`;
      } else if (Array.isArray(obj[key])) {
        return `${key}: ${obj[key].join()}`;
      } else {
        return this.getFlattenedObjects(obj[key]);
      }
    });
    return objArray.join(', ');
  }
}
