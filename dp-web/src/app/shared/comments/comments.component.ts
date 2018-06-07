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
import { Component, OnInit } from '@angular/core';
import {ActivatedRoute, Router} from "@angular/router";
import {TranslateService} from "@ngx-translate/core";
import {CommentService} from "../../services/comment.service";
import {CommentWithUserAndChildren, Comment, CommentWithUser, ReplyParent} from "../../models/comment";
import {AuthUtils} from "../utils/auth-utils";
import * as moment from 'moment';

@Component({
  selector: 'dp-comments',
  templateUrl: './comments.component.html',
  styleUrls: ['./comments.component.scss']
})
export class CommentsComponent implements OnInit {

  constructor(private router: Router,
              private route: ActivatedRoute,
              private translateService: TranslateService,
              private commentService: CommentService) { }

  isRatingEnabled: boolean = false;
  objectType: string;
  objectId: string;
  commentsWithUserAndChildren: CommentWithUserAndChildren[]= [];
  fetchInProgress: boolean =true;
  newCommentText: string;
  isReply: boolean = false;
  reply: ReplyParent;
  fetchError: boolean= false;

  ngOnInit() {
    this.objectType = this.route.snapshot.params['objectType'];
    this.objectId = this.route.parent.snapshot.params['id'];
    this.isRatingEnabled = this.route.snapshot.params['isRatingEnabled'];
    this.getComments(true);
  }

  getComments(refreshScreen: boolean) {
    this.fetchError = false;
    this.fetchInProgress = refreshScreen;
    this.commentService.getByObjectRef(this.objectId,this.objectType).subscribe(comments =>{
        this.commentsWithUserAndChildren = comments;
        this.fetchInProgress = false;
      }, () => {
        this.fetchInProgress = false;
        this.fetchError = true;
      }
    );
  }

  onPostComment() {
    if(this.newCommentText && this.newCommentText.trim()){
      let newCommentObject = new Comment();
      newCommentObject.objectType = this.objectType;
      newCommentObject.objectId = Number(this.objectId);
      newCommentObject.comment = this.newCommentText;
      newCommentObject.createdBy = Number(AuthUtils.getUser().id);
      if(this.isReply) newCommentObject.parentCommentId = this.reply.parentId;
      this.commentService.add(newCommentObject).subscribe(_ => {
        this.getComments(false);
        this.newCommentText = "";
        this.removeReply();
      });
    }
  }

  onDeleteComment(commentWU: CommentWithUser) {
    this.commentService.deleteComment(commentWU.comment.id).subscribe(_ => {
      this.getComments(false);
    });
  }

  onReplyToComment(parentCommentWU: CommentWithUser){
     this.reply = new ReplyParent();
     let parentComment = parentCommentWU.comment;
     if(parentComment.parentCommentId){
       this.reply.parentId = parentComment.parentCommentId;
     }else{
       this.reply.parentId = parentComment.id;
     }
     this.reply.commentText = parentComment.comment;
     this.reply.username = parentCommentWU.userName;
     this.isReply = true;
  }

  removeReply(){
    this.reply = new ReplyParent();
    this.isReply = false;
  }

  isLoggedInUser(commentWu: CommentWithUser){
    return Number(AuthUtils.getUser().id) === commentWu.comment.createdBy;
  }

  formatDate(dateString: string) {
    let date = moment(dateString);
    return date.format("hh:mm A MMM DD 'YY");
  }

}
