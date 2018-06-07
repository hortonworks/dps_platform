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

import {EventEmitter} from '@angular/core';
import * as DialogPolyfill from 'dialog-polyfill';


export enum DialogType {
  Confirmation, Error, DeleteConfirmation
}

export class DialogBox {

  private static getCancelButton(text: String): string {
    if (text && text.length) {
      return `<button type="button" class="mdl-button btn-hwx-default" data-se="common_util__cancelButton">${text}</button>`;
    }
    return '';
  }

  private static getOKButton(text: String, type: DialogType): string {
    if (type === DialogType.DeleteConfirmation) {
      return `<button type="button" class="mdl-button btn-hwx-warning" data-se="common_util__warningDeleteButton">${text}</button>`;
    }
    return `<button type="button" class="mdl-button btn-hwx-primary">${text}</button>`;
  }

  private static createDialogBox(message: string, okButtonText, cancelButtonText, title, type: DialogType) {
    let html = `
                    <div class="mdl-dialog__title">${title}</div>
                    <div class="mdl-dialog__content">${message}</div>
                    <div class="mdl-dialog__actions">
                    ${DialogBox.getOKButton(okButtonText, type)}${cancelButtonText ? DialogBox.getCancelButton(cancelButtonText) : ''}</div>
               `;
    let dialogElement = document.createElement('dialog');
    dialogElement.id = 'dialog';
    dialogElement.className += 'mdl-dialog dp-dialog';
    dialogElement.innerHTML = html;
    DialogPolyfill.registerDialog(dialogElement);
    document.body.appendChild(dialogElement);
    return dialogElement;
  }

  public static showConfirmationMessage(title: string, message: string, okButtonText: string, cancelButtonText?: string, dialogType = DialogType.Confirmation): EventEmitter<boolean> {
    message = message.replace(/\n/g, '<br>');
    let eventEmitter = new EventEmitter<boolean>();
    let dialog: any = DialogBox.createDialogBox(message, okButtonText, cancelButtonText, title, dialogType);
    try {
      dialog.showModal();

      if (dialogType === DialogType.DeleteConfirmation) {
        dialog.querySelector('.btn-hwx-warning').addEventListener('click', function (e) {
          eventEmitter.emit(true);
          dialog.close();
          dialog.parentElement.removeChild(dialog);
        });
      } else {
        dialog.querySelector('.btn-hwx-primary').addEventListener('click', function (e) {
          eventEmitter.emit(true);
          dialog.close();
          dialog.parentElement.removeChild(dialog);
        });
      }
      dialog.querySelector('.btn-hwx-default').addEventListener('click', function (e) {
        eventEmitter.emit(false);
        dialog.close();
        dialog.parentElement.removeChild(dialog);
      });
    } catch (e) {
    }

    return eventEmitter;
  }

  public static showErrorMessage(title: string, message: string, okButtonText: string, dialogType = DialogType.Error): EventEmitter<boolean> {
    message = message.replace(/\n/g, '<br>');
    let eventEmitter = new EventEmitter<boolean>();
    let dialog: any = DialogBox.createDialogBox(message, okButtonText, null, title, dialogType);
    try {
      dialog.showModal();

      dialog.querySelector('.btn-hwx-primary').addEventListener('click', function (e) {
        eventEmitter.emit(true);
        dialog.close();
        dialog.parentElement.removeChild(dialog);
      });
    } catch (e) {
    }

    return eventEmitter;
  }
}

