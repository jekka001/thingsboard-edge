///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import { Component, Input, OnInit } from '@angular/core';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { AlarmCommentService } from '@core/http/alarm-comment.service';
import { AbstractControl, FormBuilder, FormGroup } from '@angular/forms';
import { DialogService } from '@core/services/dialog.service';
import { AuthUser, User } from '@shared/models/user.model';
import { getCurrentAuthUser, selectUserDetails } from '@core/auth/auth.selectors';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { MAX_SAFE_PAGE_SIZE, PageLink } from '@shared/models/page/page-link';
import { DateAgoPipe } from '@shared/pipe/date-ago.pipe';
import { map } from 'rxjs/operators';
import { AlarmComment, AlarmCommentInfo, AlarmCommentType } from '@shared/models/alarm.models';
import { UtilsService } from '@core/services/utils.service';
import { EntityType } from '@shared/models/entity-type.models';

interface AlarmCommentsDisplayData {
  commentId?: string,
  displayName?: string,
  createdDateAgo?: string,
  edit?: boolean,
  isEdited?: boolean,
  editedDateAgo?: string,
  showActions?: boolean,
  commentText?: string,
  isSystemComment?: boolean,
  avatarBgColor?: string
}

@Component({
  selector: 'tb-alarm-comment',
  templateUrl: './alarm-comment.component.html',
  styleUrls: ['./alarm-comment.component.scss']
})
export class AlarmCommentComponent implements OnInit {
  @Input()
  alarmId: string;

  @Input()
  commentsHeaderEnabled: boolean = true;

  authUser: AuthUser;

  alarmCommentFormGroup: FormGroup;

  alarmComments: Array<AlarmComment>;

  displayData: Array<AlarmCommentsDisplayData> = new Array<AlarmCommentsDisplayData>();

  alarmCommentSortOrder: SortOrder = {
    property: 'createdTime',
    direction: Direction.DESC
  };

  editMode: boolean = false;

  userDisplayName$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getUserDisplayName(user))
  );

  currentUserDisplayName: string;
  currentUserAvatarColor: string;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private alarmCommentService: AlarmCommentService,
              public fb: FormBuilder,
              private dialogService: DialogService,
              public dateAgoPipe: DateAgoPipe,
              private utilsService: UtilsService) {

    this.authUser = getCurrentAuthUser(store);

    this.alarmCommentFormGroup = this.fb.group(
      {
        alarmCommentEdit: [''],
        alarmComment: ['']
      }
    );
  }

  ngOnInit() {
    this.loadAlarmComments();
    this.currentUserAvatarColor = this.utilsService.stringToHslColor(this.currentUserDisplayName,
      60, 40);
  }

  loadAlarmComments(): void {
    this.alarmCommentService.getAlarmComments(this.alarmId, new PageLink(MAX_SAFE_PAGE_SIZE, 0, null,
      this.alarmCommentSortOrder), {ignoreLoading: true}).subscribe(
      (pagedData) => {
        this.alarmComments = pagedData.data;
        this.displayData.length = 0;
        for (let alarmComment of pagedData.data) {
          let displayDataElement: AlarmCommentsDisplayData = {};
          displayDataElement.createdDateAgo = this.dateAgoPipe.transform(alarmComment.createdTime);
          displayDataElement.commentText = alarmComment.comment.text;
          displayDataElement.isSystemComment = alarmComment.type === AlarmCommentType.SYSTEM;
          if (alarmComment.type === AlarmCommentType.OTHER) {
            displayDataElement.commentId = alarmComment.id.id;
            displayDataElement.displayName = this.getUserDisplayName(alarmComment);
            displayDataElement.edit = false;
            displayDataElement.isEdited = alarmComment.comment.edited;
            displayDataElement.editedDateAgo = this.dateAgoPipe.transform(alarmComment.comment.editedOn).toLowerCase();
            displayDataElement.showActions = false;
            displayDataElement.isSystemComment = false;
            displayDataElement.avatarBgColor = this.utilsService.stringToHslColor(displayDataElement.displayName,
              40, 60);
          }
          this.displayData.push(displayDataElement);
        }
      }
    )
  }

  changeSortDirection() {
    let currentDirection = this.alarmCommentSortOrder.direction;
    this.alarmCommentSortOrder.direction = currentDirection === Direction.DESC ? Direction.ASC : Direction.DESC;
    this.loadAlarmComments();
  }

  saveComment(): void {
    const commentInputValue: string = this.getAlarmCommentFormControl().value;
    if (commentInputValue) {
      const comment: AlarmComment = {
        alarmId: {
          id: this.alarmId,
          entityType: EntityType.ALARM
        },
        type: AlarmCommentType.OTHER,
        comment: {
          text: commentInputValue
        }
      }
      this.doSave(comment);
      this.clearCommentInput();
    }
  }

  saveEditedComment(commentId: string): void {
    const commentEditInputValue: string = this.getAlarmCommentEditFormControl().value;
    if (commentEditInputValue) {
      const editedComment: AlarmComment = this.getAlarmCommentById(commentId);
      editedComment.comment.text = commentEditInputValue;
      this.doSave(editedComment);
      this.clearCommentEditInput();
      this.editMode = false;
      this.getAlarmCommentFormControl().enable({emitEvent: false});
    }
  }

  private doSave(comment: AlarmComment): void {
    this.alarmCommentService.saveAlarmComment(this.alarmId, comment, {ignoreLoading: true}).subscribe(
      () => {
        this.loadAlarmComments();
      }
    )
  }

  editComment(commentId: string): void {
    const commentDisplayData = this.getDataElementByCommentId(commentId);
    commentDisplayData.edit = true;
    this.editMode = true;
    this.getAlarmCommentEditFormControl().patchValue(commentDisplayData.commentText);
    this.getAlarmCommentFormControl().disable({emitEvent: false});
  }

  cancelEdit(commentId: string): void {
    const commentDisplayData = this.getDataElementByCommentId(commentId);
    commentDisplayData.edit = false;
    this.editMode = false;
    this.getAlarmCommentFormControl().enable({emitEvent: false});
  }

  deleteComment(commentId: string): void {
    const alarmCommentInfo: AlarmComment = this.getAlarmCommentById(commentId);
    const commentText: string = alarmCommentInfo.comment.text;
    this.dialogService.confirm(
      this.translate.instant('alarm-comment.delete-alarm-comment'),
      commentText,
      this.translate.instant('action.cancel'),
      this.translate.instant('action.delete')).subscribe(
      (result) => {
        if (result) {
          this.alarmCommentService.deleteAlarmComments(this.alarmId, commentId, {ignoreLoading: true})
            .subscribe(() => {
                this.loadAlarmComments();
              }
          )
        }
      }
    )
  }

  getSortDirectionIcon() {
    return this.alarmCommentSortOrder.direction === Direction.DESC ? 'arrow_downward' : 'arrow_upward'
  }

  isDirectionAscending() {
    return this.alarmCommentSortOrder.direction === Direction.ASC;
  }

  isDirectionDescending() {
    return this.alarmCommentSortOrder.direction === Direction.DESC;
  }

  onCommentMouseEnter(commentId: string, displayDataIndex: number): void {
    if (!this.editMode) {
      const alarmUserId = this.getAlarmCommentById(commentId).userId.id;
      if (this.authUser.userId === alarmUserId) {
        this.displayData[displayDataIndex].showActions = true;
      }
    }
  }

  onCommentMouseLeave(displayDataIndex: number): void {
    this.displayData[displayDataIndex].showActions = false;
  }

  getUserInitials(userName: string): string {
    let initials = '';
    const userNameSplit = userName.split(' ');
    initials += userNameSplit[0].charAt(0).toUpperCase();
    if (userNameSplit.length > 1) {
      initials += userNameSplit[userNameSplit.length - 1].charAt(0).toUpperCase();
    }
    return initials;
  }

  getCurrentUserBgColor(userDisplayName: string) {
    return this.utilsService.stringToHslColor(userDisplayName, 40, 60);
  }

  private getUserDisplayName(alarmCommentInfo: AlarmCommentInfo | User): string {
    let name = '';
    if ((alarmCommentInfo.firstName && alarmCommentInfo.firstName.length > 0) ||
      (alarmCommentInfo.lastName && alarmCommentInfo.lastName.length > 0)) {
      if (alarmCommentInfo.firstName) {
        name += alarmCommentInfo.firstName;
      }
      if (alarmCommentInfo.lastName) {
        if (name.length > 0) {
          name += ' ';
        }
        name += alarmCommentInfo.lastName;
      }
    } else {
      name = alarmCommentInfo.email;
    }
    return name;
  }

  getAlarmCommentFormControl(): AbstractControl {
    return this.alarmCommentFormGroup.get('alarmComment');
  }

  getAlarmCommentEditFormControl(): AbstractControl {
    return this.alarmCommentFormGroup.get('alarmCommentEdit');
  }

  private clearCommentInput(): void {
    this.getAlarmCommentFormControl().patchValue('');
  }

  private clearCommentEditInput(): void {
    this.getAlarmCommentEditFormControl().patchValue('');
  }

  private getAlarmCommentById(id: string): AlarmComment {
    return this.alarmComments.find(comment => comment.id.id === id);
  }

  private getDataElementByCommentId(commentId: string): AlarmCommentsDisplayData {
    return this.displayData.find(commentDisplayData => commentDisplayData.commentId === commentId);
  }

}
