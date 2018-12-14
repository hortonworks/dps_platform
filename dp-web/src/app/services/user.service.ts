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

import {Injectable} from '@angular/core';
import {Http, RequestOptions} from '@angular/http';
import {Observable} from 'rxjs/Observable';
import {LDAPUser} from '../models/ldap-user';
import {HttpUtil} from '../shared/utils/httpUtil';
import {User, UserList} from '../models/user';
import {Subject} from 'rxjs/Subject';
import {HttpClient} from '@angular/common/http';

@Injectable()
export class UserService {

  url = 'api/users';
  dataChanged = new Subject<boolean>();
  dataChanged$ = this.dataChanged.asObservable();

  constructor(private httpClient: HttpClient) {
  }

  getUserDetail(): Observable<User> {
    return this.httpClient.get<User>('api/identity');
  }

  searchLDAPUsers(searchTerm: string): Observable<LDAPUser[]> {
    return this.httpClient.get<LDAPUser[]>(`${this.url}/ldapsearch?name=${searchTerm}&fuzzyMatch=true`);
  }

  searchLDAPGroups(searchTerm: string): Observable<LDAPUser[]> {
    return this.httpClient.get<LDAPUser[]>(`${this.url}/ldapsearch?name=${searchTerm}&fuzzyMatch=true&searchType=group`);
  }

  addAdminUsers(users: string[]): Observable<any> {
    return this.httpClient.post<any>(`${this.url}/registerAdmins`, {users: users});
  }

  getUsersWithRole(offset: number, pageSize: number, searchTerm?: string): Observable<UserList> {
    let url = `${this.url}/withRoles?offset=${offset}&pageSize=${pageSize}`;
    if (searchTerm && searchTerm.trim().length > 0) {
      url = `${url}&searchTerm=${searchTerm}`;
    }
    return this.httpClient.get<UserList>(url);
  }

  getUserById(userId: string): Observable<User> {
    return this.httpClient.get<User>(`api/identity/${userId}`);
  }

  getUserByName(userName): Observable<User> {
    return this.httpClient.get<User>(`${this.url}/detail?userName=${userName}`);
  }

  getAllRoles(): Observable<any[]> {
    return this.httpClient.get<any[]>(`api/roles`);
  }

  addUsers(users: string[], roles: string[]): Observable<any> {
    return this.httpClient.post<any>(`${this.url}/addUsersWithRoles`, {users: users, roles: roles});
  }

  updateUser(user): Observable<any> {
    return this.httpClient.post<any>(`${this.url}/updateActiveAndRoles`, user);
  }
}
