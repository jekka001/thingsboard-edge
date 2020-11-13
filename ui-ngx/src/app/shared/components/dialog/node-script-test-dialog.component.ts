///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import {
  AfterViewInit,
  Component,
  ElementRef,
  HostBinding,
  Inject,
  OnInit,
  QueryList,
  SkipSelf,
  ViewChild,
  ViewChildren,
  ViewEncapsulation
} from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { NEVER, Observable, of } from 'rxjs';
import { Router } from '@angular/router';
import { DialogComponent } from '@shared/components/dialog.component';
import { ContentType } from '@shared/models/constants';
import { JsonContentComponent } from '@shared/components/json-content.component';
import { TestScriptInputParams } from '@shared/models/rule-node.models';
import { RuleChainService } from '@core/http/rule-chain.service';
import { mergeMap } from 'rxjs/operators';
import { ActionNotificationShow } from '@core/notification/notification.actions';

export interface NodeScriptTestDialogData {
  script: string;
  scriptType: string;
  functionTitle: string;
  functionName: string;
  argNames: string[];
  msg?: any;
  metadata?: {[key: string]: string};
  msgType?: string;
}

// @dynamic
@Component({
  selector: 'tb-node-script-test-dialog',
  templateUrl: './node-script-test-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: NodeScriptTestDialogComponent}],
  styleUrls: ['./node-script-test-dialog.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class NodeScriptTestDialogComponent extends DialogComponent<NodeScriptTestDialogComponent,
  string> implements OnInit, AfterViewInit, ErrorStateMatcher {

  @HostBinding('style.width') width = '100%';
  @HostBinding('style.height') height = '100%';

  @ViewChildren('topPanel')
  topPanelElmRef: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('topLeftPanel')
  topLeftPanelElmRef: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('topRightPanel')
  topRightPanelElmRef: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('bottomPanel')
  bottomPanelElmRef: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('bottomLeftPanel')
  bottomLeftPanelElmRef: QueryList<ElementRef<HTMLElement>>;

  @ViewChildren('bottomRightPanel')
  bottomRightPanelElmRef: QueryList<ElementRef<HTMLElement>>;

  @ViewChild('payloadContent', {static: true}) payloadContent: JsonContentComponent;

  nodeScriptTestFormGroup: FormGroup;

  functionTitle: string;

  submitted = false;

  contentTypes = ContentType;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: NodeScriptTestDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<NodeScriptTestDialogComponent, string>,
              public fb: FormBuilder,
              private ruleChainService: RuleChainService) {
    super(store, router, dialogRef);
    this.functionTitle = this.data.functionTitle;
  }

  ngOnInit(): void {
    this.nodeScriptTestFormGroup = this.fb.group({
      payload: this.fb.group({
        msgType: [this.data.msgType, [Validators.required]],
        msg: [js_beautify(JSON.stringify(this.data.msg), {indent_size: 4}), []],
      }),
      metadata: [this.data.metadata, [Validators.required]],
      script: [this.data.script, []],
      output: ['', []]
    });
  }

  ngAfterViewInit(): void {
    this.initSplitLayout(this.topPanelElmRef.first.nativeElement,
                         this.topLeftPanelElmRef.first.nativeElement,
                         this.topRightPanelElmRef.first.nativeElement,
                         this.bottomPanelElmRef.first.nativeElement,
                         this.bottomLeftPanelElmRef.first.nativeElement,
                         this.bottomRightPanelElmRef.first.nativeElement);
  }

  private initSplitLayout(topPanel: any,
                          topLeftPanel: any,
                          topRightPanel: any,
                          bottomPanel: any,
                          bottomLeftPanel: any,
                          bottomRightPanel: any) {

    Split([topPanel, bottomPanel], {
      sizes: [35, 65],
      gutterSize: 8,
      cursor: 'row-resize',
      direction: 'vertical'
    });

    Split([topLeftPanel, topRightPanel], {
      sizes: [50, 50],
      gutterSize: 8,
      cursor: 'col-resize'
    });

    Split([bottomLeftPanel, bottomRightPanel], {
      sizes: [50, 50],
      gutterSize: 8,
      cursor: 'col-resize'
    });
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  test(): void {
    this.testNodeScript().subscribe((output) => {
      this.nodeScriptTestFormGroup.get('output').setValue(js_beautify(output, {indent_size: 4}));
    });
  }

  private testNodeScript(): Observable<string> {
    if (this.checkInputParamErrors()) {
      const inputParams: TestScriptInputParams = {
        argNames: this.data.argNames,
        scriptType: this.data.scriptType,
        msgType: this.nodeScriptTestFormGroup.get('payload.msgType').value,
        msg: this.nodeScriptTestFormGroup.get('payload.msg').value,
        metadata: this.nodeScriptTestFormGroup.get('metadata').value,
        script: this.nodeScriptTestFormGroup.get('script').value
      };
      return this.ruleChainService.testScript(inputParams).pipe(
        mergeMap((result) => {
          if (result.error) {
            this.store.dispatch(new ActionNotificationShow(
              {
                message: result.error,
                type: 'error'
              }));
            return NEVER;
          } else {
            return of(result.output);
          }
        })
      );
    } else {
      return NEVER;
    }
  }

  private checkInputParamErrors(): boolean {
    this.payloadContent.validateOnSubmit();
    if (!this.nodeScriptTestFormGroup.get('payload').valid) {
      return false;
    }
    return true;
  }

  save(): void {
    this.submitted = true;
    this.testNodeScript().subscribe(() => {
      this.nodeScriptTestFormGroup.get('script').markAsPristine();
      this.dialogRef.close(this.nodeScriptTestFormGroup.get('script').value);
    });
  }
}
