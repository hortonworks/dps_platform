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
import {Persona, PersonaTabs} from '../models/header-data';
import {Observable} from 'rxjs/Observable';
import {ConfigurationService} from './configuration.service';
import {Observer} from 'rxjs/Observer';
import {AuthUtils} from '../shared/utils/auth-utils';
import {Roles} from '../shared/utils/roles';
import { PluginManifest } from '../models/plugin-manifest';

@Injectable()
export class RbacService {//role based access control

  private personaMap = new Map();
  private landingPageMap = new Map();

  constructor(private configService: ConfigurationService) {
    this.landingPageMap.set(Roles.SUPERADMIN, '/infra');
    this.landingPageMap.set(`${Roles.SUPERADMIN}_ONBOARD`, '/onboard/welcome');
    this.landingPageMap.set(Roles.INFRAADMIN, '/infra');
    this.landingPageMap.set(`${Roles.INFRAADMIN}_ONBOARD`, '/onboard');
    this.landingPageMap.set(Roles.DATASTEWARD, '/dss/');
    // temporary fix. we need to provide an app selection screen
    this.landingPageMap.set(Roles.KAFKADEVOPS, '/smm/');
    this.landingPageMap.set(Roles.DASUSER, '/das/');
    // landing page for DLM_ADMIN and DLM_USER
    this.landingPageMap.set(Roles.DLM_ADMIN, '/dlm');
    this.landingPageMap.set(Roles.DLM_USER, '/dlm');
  }

  get user() {
    return AuthUtils.getUser();
  }



  getActivePersona() {
    this.personaMap = this.getPersonaMap();
    if (this.hasRole(Roles.SUPERADMIN)) {
      return this.personaMap.get(Roles.SUPERADMIN);
    } else if (this.hasRole(Roles.INFRAADMIN)) {
      return this.personaMap.get(Roles.INFRAADMIN);
    } else if (this.hasRole(Roles.DATASTEWARD)) {
      return this.personaMap.get(Roles.DATASTEWARD);
    } else if (this.hasRole(Roles.DLM_ADMIN)) {
      return this.personaMap.get(Roles.DLM_ADMIN);
    } else if (this.hasRole(Roles.DLM_USER)) {
      return this.personaMap.get(Roles.DLM_ADMIN);
    } else {
      return this.personaMap.get(Roles.SUPERADMIN);
    } 
  }

  private getPersonaMap() {
    let personaMap = new Map();
    personaMap.set(Roles.SUPERADMIN, [
      new Persona('DataPlane Admin', [
        new PersonaTabs('Clusters', 'infra', 'fa-cubes'),
        new PersonaTabs('Users', 'infra/manage-access', 'fa-users'),
        new PersonaTabs('Services', 'infra/services', 'fa-th-large'),
        new PersonaTabs('Settings', 'infra/settings', 'fa-cog')
      ], ['/onboard', '/onboard/welcome', '/onboard/identity-provider', '/onboard/users-and-groups', '/profile/change-password'], '', 'infra-logo-white.png')
    ]);
    personaMap.set(Roles.DATASTEWARD, [
      new Persona('Data Steward Studio', [
        new PersonaTabs('Asset Collection', 'datasteward', 'fa-cubes', true),
        // new PersonaTabs('Unclassified', 'unclassified', 'fa-cube'),
        // new PersonaTabs('Assets', 'assets', 'fa-server'),
        // new PersonaTabs('Audits', 'audits', 'fa-sticky-note-o fa-sticky-note-search')
      ], ['/assets'], '', 'steward-logo.png', !!this.user && this.user.services.indexOf('dss') > -1)]);
    personaMap.set(Roles.INFRAADMIN, [
      new Persona('Infra Admin', [
        new PersonaTabs('Clusters', 'infra', 'fa-sitemap')
      ], [], '', 'infra-logo.png'),
      new Persona('Data Lifecycle Manager', [], [], '/dlm', 'dlm-logo.png', !!this.user && this.user.services.indexOf('dlm') > -1)]);
    personaMap.set(`${Roles.INFRAADMIN}_${Roles.SUPERADMIN}`, [
      new Persona('Data Lifecycle Manager', [], [], '/dlm', 'dlm-logo.png', !!this.user && this.user.services.indexOf('dlm') > -1)
    ]);
    personaMap.set(Roles.DLM_ADMIN,[
      new Persona('Data Lifecycle Manager', [], [], '/dlm', 'dlm-logo.png', !!this.user && this.user.services.indexOf('dlm') > -1)
    ]);
    personaMap.set(Roles.DLM_USER,[
      new Persona('Data Lifecycle Manager', [], [], '/dlm', 'dlm-logo.png', !!this.user && this.user.services.indexOf('dlm') > -1)
    ]);
    return personaMap;
  }

  private getLandingInternal(observer: Observer<string>, key: String, type?: String) {
    if(type){
      observer.next(this.landingPageMap.get(`${key}_${type}`));
    }else{
      observer.next(this.landingPageMap.get(key));
    }
    observer.complete();
  }
  
  getLandingPage(isLakeInitialized: boolean, plugins: PluginManifest[]): Observable<string> {
    return Observable.create(observer => {
      if (!this.user.roles || this.user.roles.length === 0) {
        observer.next('/unauthorized');
        observer.complete();
        return;
      }

      // create a map of role and if app is active
      if (this.hasRole(Roles.SUPERADMIN)) {
        this.configService.isKnoxConfigured().subscribe(response => {
          if (!response.configured) {
            return this.getLandingInternal(observer, Roles.SUPERADMIN, 'ONBOARD');
          }
          if (isLakeInitialized) {
            return this.getLandingInternal(observer, Roles.INFRAADMIN);
          } else {
            return this.getLandingInternal(observer, Roles.INFRAADMIN, 'ONBOARD');
          }
        });
      } else if (this.hasRole(Roles.INFRAADMIN)) {
        return this.getLandingInternal(observer, Roles.INFRAADMIN);
      } else if (plugins.length > 0) {
        // if we got any healthy app redirect to first one
        observer.next(plugins[0].prefix);
        observer.complete();
      } else if (this.hasRole(Roles.DATASTEWARD)) {
        return this.getLandingInternal(observer, Roles.DATASTEWARD);
      } else if (this.hasRole(Roles.KAFKADEVOPS)) {
        return this.getLandingInternal(observer, Roles.KAFKADEVOPS);
      } else if (this.hasRole(Roles.DASUSER)) {
        return this.getLandingInternal(observer, Roles.DASUSER);
      } else if (this.hasRole(Roles.DLM_ADMIN)) {
        return this.getLandingInternal(observer, Roles.DLM_ADMIN);
      } else if (this.hasRole(Roles.DLM_USER)) {
        return this.getLandingInternal(observer, Roles.DLM_USER);
      }
    });
  }

  private hasRole(userRole) {
    let roles = this.user.roles;
    return roles.find(role => role === userRole);
  }

  isAuthorized(route: string): boolean {
    let personas = this.getPersonaDetails();
    return this.isAuthorizedPersonaRoute(personas, route) || this.isAuthorizedNonPersonaRoute(personas, route);
  }

  isServiceEnabled(route: string): boolean {
    let personas = this.getPersonaDetails();
    return !personas.find(persona => !persona.enabled && !!persona.tabs.find(tab => route.startsWith(`/${tab.URL}`)));
  }

  private isAuthorizedPersonaRoute(personas, route: string): boolean {
    return !!personas.find(persona => !!persona.tabs.find(tab => route.startsWith(`/${tab.URL}`)));
  }

  private isAuthorizedNonPersonaRoute(personas, route: string): boolean {
    return !!personas.find(persona => !!persona.nonTabUrls.find(url => route.startsWith(`${url}`)));
  }

  getPersonaDetails() {
    let personas = [];
    this.personaMap = this.getPersonaMap();
    let isSuperAdmin = false;
    if (this.hasRole(Roles.SUPERADMIN)) {
      isSuperAdmin = true;
      personas.push(...this.personaMap.get(Roles.SUPERADMIN));
    }
    if (this.hasRole(Roles.INFRAADMIN) && !isSuperAdmin) {
      personas.push(...this.personaMap.get(Roles.INFRAADMIN));
    } else if (this.hasRole(Roles.INFRAADMIN) && isSuperAdmin) {
      personas.push(...this.personaMap.get(`${Roles.INFRAADMIN}_${Roles.SUPERADMIN}`));
    }
    if (this.hasRole(Roles.DATASTEWARD)) {
      personas.push(...this.personaMap.get(Roles.DATASTEWARD));
    }
    if (this.hasRole(Roles.DLM_ADMIN)) {
      personas.push(...this.personaMap.get(Roles.DLM_ADMIN));
    }
    if (this.hasRole(Roles.DLM_USER)) {
      personas.push(...this.personaMap.get(Roles.DLM_USER));
    }
    return personas;
  }
}
