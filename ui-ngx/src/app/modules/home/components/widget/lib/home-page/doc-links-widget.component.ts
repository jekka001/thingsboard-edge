///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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
import { map, Subscription } from 'rxjs';
import { DocumentationLink, DocumentationLinks } from '@shared/models/user-settings.models';
import { UserSettingsService } from '@core/http/user-settings.service';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { WidgetContext } from '@home/models/widget-component.models';
import { MatDialog } from '@angular/material/dialog';
import { AddDocLinkDialogComponent } from '@home/components/widget/lib/home-page/add-doc-link-dialog.component';
import {
  EditLinksDialogComponent,
  EditLinksDialogData
} from '@home/components/widget/lib/home-page/edit-links-dialog.component';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { docPlatformPrefix, MediaBreakpoints } from '@shared/models/constants';
import { WhiteLabelingService } from '@core/http/white-labeling.service';
import { deepClone } from '@core/utils';

const defaultDocLinksMap = new Map<Authority, DocumentationLinks>(
  [
    [Authority.SYS_ADMIN, {
      links: [
        {
          icon: 'rocket',
          name: 'Getting started',
          link: '${baseUrl}getting-started-guides/helloworld/'
        },
        {
          icon: 'title',
          name: 'Tenant profiles',
          link: '${baseUrl}user-guide/tenant-profiles/'
        },
        {
          icon: 'insert_chart',
          name: 'API',
          link: '${baseUrl}api/'
        },
        {
          icon: 'now_widgets',
          name: 'Widgets Library',
          link: '${baseUrl}user-guide/ui/widget-library/'
        }
      ]
    }],
    [Authority.TENANT_ADMIN, {
      links: [
        {
          icon: 'rocket',
          name: 'Getting started',
          link: '${baseUrl}getting-started-guides/helloworld/'
        },
        {
          icon: 'settings_ethernet',
          name: 'Rule engine',
          link: '${baseUrl}user-guide/rule-engine-2-0/re-getting-started/'
        },
        {
          icon: 'insert_chart',
          name: 'API',
          link: '${baseUrl}api/'
        },
        {
          icon: 'devices',
          name: 'Device profiles',
          link: '${baseUrl}user-guide/device-profiles/'
        }
      ]
    }],
    [Authority.CUSTOMER_USER, {
      links: [
        {
          icon: 'rocket',
          name: 'Getting started',
          link: '${baseUrl}getting-started-guides/helloworld/'
        }
      ]
    }]
  ]
);

interface DocLinksWidgetSettings {
  columns: number;
}

@Component({
  selector: 'tb-doc-links-widget',
  templateUrl: './doc-links-widget.component.html',
  styleUrls: ['./home-page-widget.scss', './links-widget.component.scss']
})
export class DocLinksWidgetComponent extends PageComponent implements OnInit, OnDestroy {

  @Input()
  ctx: WidgetContext;

  settings: DocLinksWidgetSettings;
  columns: number;
  rowHeight = '55px';
  gutterSize = '12px';

  documentationLinks: DocumentationLinks;
  authUser = getCurrentAuthUser(this.store);

  docsLink: string;

  private observeBreakpointSubscription: Subscription;

  constructor(protected store: Store<AppState>,
              private cd: ChangeDetectorRef,
              private userSettingsService: UserSettingsService,
              private dialog: MatDialog,
              private wl: WhiteLabelingService,
              private breakpointObserver: BreakpointObserver) {
    super(store);
  }

  ngOnInit() {
    this.docsLink = this.wl.getHelpLinkBaseUrl() + `/docs${docPlatformPrefix}/`;
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
    this.loadDocLinks();
  }

  ngOnDestroy() {
    if (this.observeBreakpointSubscription) {
      this.observeBreakpointSubscription.unsubscribe();
    }
    super.ngOnDestroy();
  }

  private loadDocLinks() {
    this.userSettingsService.getDocumentationLinks().pipe(
      map((documentationLinks) => {
        if (!documentationLinks || !documentationLinks.links) {
          return this.updateBaseUrls(defaultDocLinksMap.get(this.authUser.authority));
        } else {
          return documentationLinks;
        }
      })
    ).subscribe(
      (documentationLinks) => {
        this.documentationLinks = documentationLinks;
        this.cd.markForCheck();
      }
    );
  }

  private updateBaseUrls(documentationLinks: DocumentationLinks): DocumentationLinks {
    const result = deepClone(documentationLinks);
    for (const link of result.links) {
      link.link = link.link.replace('${baseUrl}', this.docsLink);
    }
    return result;
  }

  edit() {
    this.dialog.open<EditLinksDialogComponent, EditLinksDialogData,
      boolean>(EditLinksDialogComponent, {
      disableClose: true,
      autoFocus: false,
      data: {
        mode: 'docs',
        links: this.documentationLinks
      },
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed().subscribe(
      (result) => {
        if (result) {
          this.loadDocLinks();
        }
      });
  }

  addLink() {
    this.dialog.open<AddDocLinkDialogComponent, any,
      DocumentationLink>(AddDocLinkDialogComponent, {
      disableClose: true,
      autoFocus: false,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed().subscribe(
      (docLink) => {
        if (docLink) {
          this.documentationLinks.links.push(docLink);
          this.cd.markForCheck();
          this.userSettingsService.updateDocumentationLinks(this.documentationLinks).subscribe();
        }
    });
  }
}
