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

import { ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Authority } from '@shared/models/authority.enum';
import { map, Observable, of, Subscription } from 'rxjs';
import { QuickLinks } from '@shared/models/user-settings.models';
import { UserSettingsService } from '@core/http/user-settings.service';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { WidgetContext } from '@home/models/widget-component.models';
import { MatDialog } from '@angular/material/dialog';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { MenuService } from '@core/services/menu.service';
import { MenuSection } from '@core/services/menu.models';
import { AddQuickLinkDialogComponent } from '@home/components/widget/lib/home-page/add-quick-link-dialog.component';
import {
  EditLinksDialogComponent,
  EditLinksDialogData
} from '@home/components/widget/lib/home-page/edit-links-dialog.component';

const defaultQuickLinksMap = new Map<Authority, QuickLinks>(
  [
    [Authority.SYS_ADMIN, {
      links: ['tenants', 'tenant_profiles']
    }],
    [Authority.TENANT_ADMIN, {
      links: ['alarms', 'dashboards', 'devices']
    }],
    [Authority.CUSTOMER_USER, {
      links: ['alarms', 'dashboards', 'devices']
    }]
  ]
);

interface QuickLinksWidgetSettings {
  columns: number;
}

@Component({
  selector: 'tb-quick-links-widget',
  templateUrl: './quick-links-widget.component.html',
  styleUrls: ['./home-page-widget.scss', './links-widget.component.scss']
})
export class QuickLinksWidgetComponent extends PageComponent implements OnInit, OnDestroy {

  @Input()
  ctx: WidgetContext;

  settings: QuickLinksWidgetSettings;
  columns: number;
  rowHeight = '55px';
  gutterSize = '12px';

  quickLinks: QuickLinks;
  authUser = getCurrentAuthUser(this.store);

  private observeBreakpointSubscription: Subscription;

  constructor(protected store: Store<AppState>,
              private cd: ChangeDetectorRef,
              private userSettingsService: UserSettingsService,
              private dialog: MatDialog,
              private menuService: MenuService,
              private breakpointObserver: BreakpointObserver) {
    super(store);
  }

  ngOnInit() {
    this.settings = this.ctx.settings;
    this.columns = this.settings.columns || 3;
    const isMdLg = this.breakpointObserver.isMatched(MediaBreakpoints['md-lg']);
    this.rowHeight = isMdLg ? '18px' : '55px';
    this.gutterSize = isMdLg ? '8px' : '12px';
    this.observeBreakpointSubscription = this.breakpointObserver
      .observe(MediaBreakpoints['md-lg'])
      .subscribe((state: BreakpointState) => {
          if (state.matches) {
            this.rowHeight = '18px';
            this.gutterSize = '8px';
          } else {
            this.rowHeight = '55px';
            this.gutterSize = '12px';
          }
          this.cd.markForCheck();
        }
      );
    this.loadQuickLinks();
  }

  ngOnDestroy() {
    if (this.observeBreakpointSubscription) {
      this.observeBreakpointSubscription.unsubscribe();
    }
    super.ngOnDestroy();
  }

  menuLinks$(): Observable<Array<MenuSection>> {
    return this.quickLinks ? this.menuService.menuLinksByIds(this.quickLinks.links) : of([]);
  }

  private loadQuickLinks() {
    this.userSettingsService.getQuickLinks().pipe(
      map((quickLinks) => {
        if (!quickLinks || !quickLinks.links) {
          return defaultQuickLinksMap.get(this.authUser.authority);
        } else {
          return quickLinks;
        }
      })
    ).subscribe(
      (quickLinks) => {
        this.quickLinks = quickLinks;
        this.cd.markForCheck();
      }
    );
  }

  edit() {
    this.dialog.open<EditLinksDialogComponent, EditLinksDialogData,
      boolean>(EditLinksDialogComponent, {
      disableClose: true,
      autoFocus: false,
      data: {
        mode: 'quickLinks',
        links: this.quickLinks
      },
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed().subscribe(
      (result) => {
        if (result) {
          this.loadQuickLinks();
        }
      });
  }

  addLink() {
    this.dialog.open<AddQuickLinkDialogComponent, any,
      string>(AddQuickLinkDialogComponent, {
      disableClose: true,
      autoFocus: false,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed().subscribe(
      (link) => {
        if (link) {
          this.quickLinks.links.push(link);
          this.cd.markForCheck();
          this.userSettingsService.updateQuickLinks(this.quickLinks).subscribe();
        }
      });
  }
}
