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

import {
  AfterContentInit,
  ChangeDetectorRef,
  Component,
  ContentChildren,
  Directive,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  QueryList
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Subject, Subscription } from 'rxjs';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { coerceBoolean } from '@shared/decorators/coercion';
import { startWith, takeUntil } from 'rxjs/operators';

export interface ToggleHeaderOption {
  name: string;
  value: any;
}

export type ToggleHeaderAppearance = 'fill' | 'fill-invert' | 'stroked';

@Directive(
  {
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: 'tb-toggle-option',
  }
)
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export class ToggleOption {

  @Input() value: any;

  get viewValue(): string {
    return (this._element?.nativeElement.textContent || '').trim();
  }

  constructor(
    private _element: ElementRef<HTMLElement>
  ) {}
}

@Directive()
export abstract class _ToggleBase extends PageComponent implements AfterContentInit, OnDestroy {

  @ContentChildren(ToggleOption) toggleOptions: QueryList<ToggleOption>;

  @Input()
  options: ToggleHeaderOption[] = [];

  private _destroyed = new Subject<void>();

  protected constructor(protected store: Store<AppState>) {
    super(store);
  }

  ngAfterContentInit(): void {
    this.toggleOptions.changes.pipe(startWith(null), takeUntil(this._destroyed)).subscribe(() => {
      this.syncToggleHeaderOptions();
    });
  }

  ngOnDestroy() {
    this._destroyed.next();
    this._destroyed.complete();
  }

  private syncToggleHeaderOptions() {
    if (this.toggleOptions?.length) {
      this.options.length = 0;
      this.toggleOptions.forEach(option => {
        this.options.push(
          { name: option.viewValue,
            value: option.value
          }
        );
      });
    }
  }

}

@Component({
  selector: 'tb-toggle-header',
  templateUrl: './toggle-header.component.html',
  styleUrls: ['./toggle-header.component.scss']
})
export class ToggleHeaderComponent extends _ToggleBase implements OnInit, AfterContentInit, OnDestroy {

  @Input()
  value: any;

  @Output()
  valueChange = new EventEmitter<any>();

  @Input()
  name: string;

  @Input()
  @coerceBoolean()
  useSelectOnMdLg = true;

  @Input()
  @coerceBoolean()
  ignoreMdLgSize = false;

  @Input()
  appearance: ToggleHeaderAppearance = 'stroked';

  isMdLg: boolean;

  private observeBreakpointSubscription: Subscription;

  constructor(protected store: Store<AppState>,
              private cd: ChangeDetectorRef,
              private breakpointObserver: BreakpointObserver) {
    super(store);
  }

  ngOnInit() {
    this.isMdLg = this.breakpointObserver.isMatched(MediaBreakpoints['md-lg']);
    this.observeBreakpointSubscription = this.breakpointObserver
      .observe(MediaBreakpoints['md-lg'])
      .subscribe((state: BreakpointState) => {
          this.isMdLg = state.matches;
          this.cd.markForCheck();
        }
      );
  }

  trackByHeaderOption(index: number, option: ToggleHeaderOption){
    return option.value;
  }
}
