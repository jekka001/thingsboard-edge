///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import { AfterViewInit, Component, ElementRef, Input, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { WidgetContext } from '@home/models/widget-component.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { Operation, Resource } from '@shared/models/security.models';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import {
  SchedulerEvent,
  SchedulerEventWithCustomerInfo,
  SchedulerRepeatType,
  schedulerRepeatTypeToUnitMap, schedulerTimeUnitRepeatTranslationMap,
  schedulerWeekday
} from '@shared/models/scheduler-event.models';
import { CollectionViewer, DataSource, SelectionModel } from '@angular/cdk/collections';
import { BehaviorSubject, forkJoin, fromEvent, merge, Observable, of, ReplaySubject } from 'rxjs';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  publishReplay,
  refCount,
  take,
  tap
} from 'rxjs/operators';
import { PageLink } from '@shared/models/page/page-link';
import { SchedulerEventService } from '@core/http/scheduler-event.service';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { Direction, SortOrder, sortOrderFromString } from '@shared/models/page/sort-order';
import { TranslateService } from '@ngx-translate/core';
import { deepClone, isDefined, isNumber } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import {
  SchedulerEventDialogComponent,
  SchedulerEventDialogData
} from '@home/components/scheduler/scheduler-event-dialog.component';
import {
  defaultSchedulerEventConfigTypes,
  SchedulerEventConfigType
} from '@home/components/scheduler/scheduler-event-config.models';
import { DialogService } from '@core/services/dialog.service';
import dayGridPlugin from '@fullcalendar/daygrid';
import listPlugin from '@fullcalendar/list';
import timeGridPlugin from '@fullcalendar/timegrid';
import momentPlugin, { toMoment } from '@fullcalendar/moment';
import interactionPlugin from '@fullcalendar/interaction';
import { FullCalendarComponent } from '@fullcalendar/angular';
import {
  schedulerCalendarView,
  schedulerCalendarViewTranslationMap,
  schedulerCalendarViewValueMap,
  SchedulerEventsWidgetSettings
} from '@home/components/scheduler/scheduler-events.models';
import { Calendar, DateClickApi } from '@fullcalendar/core/Calendar';
import { asRoughMs, Duration, EventInput, rangeContainsMarker } from '@fullcalendar/core';
import { EventSourceError, EventSourceInput } from '@fullcalendar/core/structs/event-source';
import * as _moment from 'moment';
import { MatMenuTrigger } from '@angular/material/menu';
import { EventHandlerArg } from '@fullcalendar/core/types/input-types';
import { getUserZone } from '@shared/models/time/time.models';

@Component({
  selector: 'tb-scheduler-events',
  templateUrl: './scheduler-events.component.html',
  styleUrls: ['./scheduler-events.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class SchedulerEventsComponent extends PageComponent implements OnInit, AfterViewInit {

  @ViewChild('searchInput') searchInputField: ElementRef;

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;

  @ViewChild('calendarContainer') calendarContainer: ElementRef<HTMLElement>;
  @ViewChild('calendar') calendarComponent: FullCalendarComponent;

  @Input()
  widgetMode: boolean;

  @Input()
  ctx: WidgetContext;

  settings: SchedulerEventsWidgetSettings;

  editEnabled = this.userPermissionsService.hasGenericPermission(Resource.SCHEDULER_EVENT, Operation.WRITE);
  addEnabled = this.userPermissionsService.hasGenericPermission(Resource.SCHEDULER_EVENT, Operation.CREATE);
  deleteEnabled = this.userPermissionsService.hasGenericPermission(Resource.SCHEDULER_EVENT, Operation.DELETE);

  authUser = getCurrentAuthUser(this.store);

  showData = (this.authUser.authority === Authority.TENANT_ADMIN ||
    this.authUser.authority === Authority.CUSTOMER_USER) &&
    this.userPermissionsService.hasGenericPermission(Resource.SCHEDULER_EVENT, Operation.READ);

  mode = 'list';

  displayCreatedTime = true;
  displayType = true;
  displayCustomer = true;

  schedulerEventConfigTypes: {[eventType: string]: SchedulerEventConfigType};

  displayPagination = true;
  pageSizeOptions;
  defaultPageSize = 10;
  defaultSortOrder = 'createdTime';
  defaultEventType: string;

  displayedColumns: string[];
  pageLink: PageLink;

  textSearchMode = false;

  dataSource: SchedulerEventsDatasource;

  calendarPlugins = [interactionPlugin, momentPlugin, dayGridPlugin, listPlugin, timeGridPlugin];

  currentCalendarView = schedulerCalendarView.month;

  currentCalendarViewValue = schedulerCalendarViewValueMap.get(this.currentCalendarView);

  schedulerCalendarViews = Object.keys(schedulerCalendarView);
  schedulerCalendarViewTranslations = schedulerCalendarViewTranslationMap;

  eventSources: EventSourceInput[] = [this.eventSourceFunction.bind(this)];

  private schedulerEvents: Array<SchedulerEventWithCustomerInfo> = [];

  calendarApi: Calendar;

  @ViewChild('schedulerEventMenuTrigger', {static: true}) schedulerEventMenuTrigger: MatMenuTrigger;

  schedulerEventMenuPosition = { x: '0px', y: '0px' };

  schedulerContextMenuEvent: MouseEvent;

  constructor(protected store: Store<AppState>,
              public translate: TranslateService,
              private schedulerEventService: SchedulerEventService,
              private userPermissionsService: UserPermissionsService,
              private dialogService: DialogService,
              private dialog: MatDialog) {
    super(store);
  }

  ngOnInit(): void {
    if (this.widgetMode) {
      this.ctx.$scope.schedulerEventsWidget = this;
    }
    if (this.showData && this.widgetMode) {
      this.settings = this.ctx.settings;
      this.initializeWidgetConfig();
      this.ctx.updateWidgetParams();
    } else {
      this.displayedColumns = ['createdTime', 'name', 'typeName', 'customerTitle', 'actions'];
      if (this.deleteEnabled) {
        this.displayedColumns.unshift('select');
      }
      const sortOrder: SortOrder = { property: this.defaultSortOrder, direction: Direction.ASC };
      this.pageSizeOptions = [this.defaultPageSize, this.defaultPageSize * 2, this.defaultPageSize * 3];
      this.pageLink = new PageLink(this.defaultPageSize, 0, null, sortOrder);
      this.schedulerEventConfigTypes = deepClone(defaultSchedulerEventConfigTypes);
      this.dataSource = new SchedulerEventsDatasource(this.schedulerEventService, this.schedulerEventConfigTypes);
    }
  }

  private initializeWidgetConfig() {
    this.ctx.widgetConfig.showTitle = false;
    this.ctx.widgetTitle = this.settings.title;
    const displayCreatedTime = isDefined(this.settings.displayCreatedTime) ? this.settings.displayCreatedTime : true;
    const displayType = isDefined(this.settings.displayType) ? this.settings.displayType : true;
    const displayCustomer = isDefined(this.settings.displayCustomer) ? this.settings.displayCustomer : true;

    this.displayedColumns = [];
    if (this.deleteEnabled) {
      this.displayedColumns.push('select');
    }
    if (displayCreatedTime) {
      this.displayedColumns.push('createdTime');
    }
    this.displayedColumns.push('name');
    if (displayType) {
      this.displayedColumns.push('typeName');
    }
    if (displayCustomer) {
      this.displayedColumns.push('customerTitle');
    }
    this.displayedColumns.push('actions');
    this.displayPagination = isDefined(this.settings.displayPagination) ? this.settings.displayPagination : true;
    const pageSize = this.settings.defaultPageSize;
    if (isDefined(pageSize) && isNumber(pageSize) && pageSize > 0) {
      this.defaultPageSize = pageSize;
    }
    this.pageSizeOptions = [this.defaultPageSize, this.defaultPageSize * 2, this.defaultPageSize * 3];
    if (this.settings.defaultSortOrder && this.settings.defaultSortOrder.length) {
      this.defaultSortOrder = this.settings.defaultSortOrder;
    }
    const sortOrder: SortOrder = sortOrderFromString(this.defaultSortOrder);
    if (sortOrder.property === 'type') {
      sortOrder.property = 'typeName';
    }
    if (sortOrder.property === 'customer') {
      sortOrder.property = 'customerTitle';
    }
    this.pageLink = new PageLink(this.defaultPageSize, 0, null, sortOrder);
    if (this.settings.forceDefaultEventType && this.settings.forceDefaultEventType.length) {
      this.defaultEventType = this.settings.forceDefaultEventType;
    }
    this.schedulerEventConfigTypes = deepClone(defaultSchedulerEventConfigTypes);
    if (this.settings.customEventTypes && this.settings.customEventTypes.length) {
      this.settings.customEventTypes.forEach((customEventType) => {
        this.schedulerEventConfigTypes[customEventType.value] = customEventType;
      });
    }
    if (this.settings.enabledViews !== 'both') {
      this.mode = this.settings.enabledViews;
    }
    this.ctx.widgetActions = [
      {
        name: 'scheduler.add-scheduler-event',
        show: this.addEnabled,
        icon: 'add',
        onAction: ($event) => {
          this.addSchedulerEvent($event);
        }
      },
      {
        name: 'action.search',
        show: true,
        icon: 'search',
        onAction: () => {
          this.enterFilterMode();
        }
      },
      {
        name: 'action.refresh',
        show: true,
        icon: 'refresh',
        onAction: () => {
          this.reloadSchedulerEvents();
        }
      }
    ];
    this.dataSource = new SchedulerEventsDatasource(this.schedulerEventService, this.schedulerEventConfigTypes);
    this.dataSource.selection.changed.subscribe(() => {
      const hideTitlePanel = !this.dataSource.selection.isEmpty() || this.textSearchMode;
      if (this.ctx.hideTitlePanel !== hideTitlePanel) {
        this.ctx.hideTitlePanel = hideTitlePanel;
        this.ctx.detectChanges(true);
      } else {
        this.ctx.detectChanges();
      }
    });
  }

  ngAfterViewInit() {
    if (this.showData) {
      setTimeout(() => {
        this.calendarApi = this.calendarComponent.getApi();
        this.calendarApi.refetchEvents();
        if (this.widgetMode && this.mode === 'calendar') {
          this.resize();
        }
      }, 0);

      fromEvent(this.searchInputField.nativeElement, 'keyup')
        .pipe(
          debounceTime(150),
          distinctUntilChanged(),
          tap(() => {
            if (this.displayPagination) {
              this.paginator.pageIndex = 0;
            }
            this.updateData();
          })
        )
        .subscribe();

      if (this.displayPagination) {
        this.sort.sortChange.subscribe(() => this.paginator.pageIndex = 0);
      }

      ((this.displayPagination ? merge(this.sort.sortChange, this.paginator.page) : this.sort.sortChange) as Observable<any>)
        .pipe(
          tap(() => this.updateData())
        )
        .subscribe();

      this.updateData(true);
    }
  }

  resize() {
    if (this.mode === 'calendar' && this.calendarApi) {
      this.calendarApi.updateSize();
    }
  }

  updateData(reload: boolean = false) {
    if (this.displayPagination) {
      this.pageLink.page = this.paginator.pageIndex;
      this.pageLink.pageSize = this.paginator.pageSize;
    } else {
      this.pageLink.page = 0;
    }
    this.pageLink.sortOrder.property = this.sort.active;
    this.pageLink.sortOrder.direction = Direction[this.sort.direction.toUpperCase()];
    this.dataSource.loadEntities(this.pageLink, this.defaultEventType, reload).subscribe(
      (data) => {
        this.updateCalendarEvents(data.data);
      }
    );
    if (this.widgetMode) {
      this.ctx.detectChanges();
    }
  }

  enterFilterMode() {
    this.textSearchMode = true;
    this.pageLink.textSearch = '';
    if (this.widgetMode) {
      this.ctx.hideTitlePanel = true;
      this.ctx.detectChanges(true);
    }
    setTimeout(() => {
      this.searchInputField.nativeElement.focus();
      this.searchInputField.nativeElement.setSelectionRange(0, 0);
    }, 10);
  }

  exitFilterMode() {
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    if (this.displayPagination) {
      this.paginator.pageIndex = 0;
    }
    this.updateData();
    if (this.widgetMode) {
      this.ctx.hideTitlePanel = false;
      this.ctx.detectChanges(true);
    }
  }

  reloadSchedulerEvents() {
    this.updateData(true);
  }

  deleteSchedulerEvent($event: Event, schedulerEvent: SchedulerEventWithCustomerInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('scheduler.delete-scheduler-event-title', {schedulerEventName: schedulerEvent.name});
    const content = this.translate.instant('scheduler.delete-scheduler-event-text');
    this.dialogService.confirm(title, content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes')).subscribe((result) => {
      if (result) {
        this.schedulerEventService.deleteSchedulerEvent(schedulerEvent.id.id).subscribe(
          () => {
            this.reloadSchedulerEvents();
          }
        );
      }
    });
  }

  deleteSchedulerEvents($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    const selectedSchedulerEvents = this.dataSource.selection.selected;
    if (selectedSchedulerEvents && selectedSchedulerEvents.length) {
      const title = this.translate.instant('scheduler.delete-scheduler-events-title', {count: selectedSchedulerEvents.length});
      const content = this.translate.instant('scheduler.delete-scheduler-events-text');
      this.dialogService.confirm(title, content,
        this.translate.instant('action.no'),
        this.translate.instant('action.yes')).subscribe((result) => {
        if (result) {
          const tasks = selectedSchedulerEvents.map((schedulerEvent) =>
            this.schedulerEventService.deleteSchedulerEvent(schedulerEvent.id.id));
          forkJoin(tasks).subscribe(
            () => {
              this.reloadSchedulerEvents();
            }
          );
        }
      });
    }
  }

  addSchedulerEvent($event: Event) {
    this.openSchedulerEventDialog($event);
  }

  editSchedulerEvent($event, schedulerEventWithCustomerInfo: SchedulerEventWithCustomerInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.schedulerEventService.getSchedulerEvent(schedulerEventWithCustomerInfo.id.id)
      .subscribe((schedulerEvent) => {
      this.openSchedulerEventDialog($event, schedulerEvent);
    });
  }

  viewSchedulerEvent($event, schedulerEventWithCustomerInfo: SchedulerEventWithCustomerInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.schedulerEventService.getSchedulerEvent(schedulerEventWithCustomerInfo.id.id)
      .subscribe((schedulerEvent) => {
        this.openSchedulerEventDialog($event, schedulerEvent, true);
      });
  }

  openSchedulerEventDialog($event: Event, schedulerEvent?: SchedulerEvent, readonly = false) {
    if ($event) {
      $event.stopPropagation();
    }
    let isAdd = false;
    if (!schedulerEvent || !schedulerEvent.id) {
      isAdd = true;
      if (!schedulerEvent) {
        schedulerEvent = {
          name: null,
          type: null,
          schedule: null,
          configuration: {
            originatorId: null,
            msgType: null,
            msgBody: {},
            metadata: {}
          }
        };
      }
    }
    this.dialog.open<SchedulerEventDialogComponent, SchedulerEventDialogData, boolean>(SchedulerEventDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        schedulerEventConfigTypes: this.schedulerEventConfigTypes,
        isAdd,
        readonly,
        schedulerEvent,
        defaultEventType: this.defaultEventType
      }
    }).afterClosed().subscribe(
      (res) => {
        if (res) {
          this.reloadSchedulerEvents();
        }
      }
    );
  }

  triggerResize() {
    setTimeout(() => {
      this.calendarComponent.getApi().updateSize();
    }, 0);
  }

  changeCalendarView() {
    this.currentCalendarViewValue = schedulerCalendarViewValueMap.get(this.currentCalendarView);
    this.calendarApi.changeView(this.currentCalendarViewValue);
  }

  calendarViewTitle(): string {
    if (this.calendarApi) {
      return this.calendarApi.view.title;
    } else {
      return '';
    }
  }

  gotoCalendarToday() {
    this.calendarApi.today();
  }

  isCalendarToday(): boolean {
    if (this.calendarApi) {
      const now = this.calendarApi.getNow();
      const view = this.calendarApi.view;
      return rangeContainsMarker(view.props.dateProfile.currentRange, now);
    } else {
      return false;
    }
  }

  gotoCalendarPrev() {
    this.calendarApi.prev();
  }

  gotoCalendarNext() {
    this.calendarApi.next();
  }

  onEventClick(arg: EventHandlerArg<'eventClick'>) {
    const schedulerEvent = this.schedulerEvents.find(event => event.id.id === arg.event.id);
    if (schedulerEvent) {
      this.openSchedulerEventContextMenu(arg.jsEvent, schedulerEvent);
    }
  }

  openSchedulerEventContextMenu($event: MouseEvent, schedulerEvent: SchedulerEventWithCustomerInfo) {
    $event.preventDefault();
    $event.stopPropagation();
    const $element = $(this.calendarContainer.nativeElement);
    const offset = $element.offset();
    const x = $event.pageX - offset.left;
    const y = $event.pageY - offset.top;
    this.schedulerContextMenuEvent = $event;
    this.schedulerEventMenuPosition.x = x + 'px';
    this.schedulerEventMenuPosition.y = y + 'px';
    this.schedulerEventMenuTrigger.menuData = { schedulerEvent };
    this.schedulerEventMenuTrigger.openMenu();
  }

  onSchedulerEventContextMenuMouseLeave() {
    this.schedulerEventMenuTrigger.closeMenu();
  }

  onDayClick(event: DateClickApi) {
    if (this.addEnabled) {
      const calendarDate = new Date(event.date.getTime() + event.date.getTimezoneOffset() * 60 * 1000);
      const date = toMoment(calendarDate, this.calendarApi);
      const schedulerEvent = {
        schedule: {
          startTime: date.utc().valueOf()
        },
        configuration: {}
      } as SchedulerEvent;
      this.openSchedulerEventDialog(event.jsEvent, schedulerEvent);
    }
  }

  onEventDrop(arg: EventHandlerArg<'eventDrop'>) {
    const schedulerEvent = this.schedulerEvents.find(event => event.id.id === arg.event.id);
    if (schedulerEvent) {
      this.moveEvent(schedulerEvent, arg.delta, arg.revert);
    }
  }

  private moveEvent(event: SchedulerEventWithCustomerInfo, delta: Duration, revertFunc: () => void) {
    this.schedulerEventService.getSchedulerEvent(event.id.id).subscribe(
      (schedulerEvent) => {
        schedulerEvent.schedule.startTime += asRoughMs(delta);
        this.schedulerEventService.saveSchedulerEvent(schedulerEvent).subscribe(
          () => {
            this.reloadSchedulerEvents();
          },
          () => {
            revertFunc();
          }
        );
      },
      () => {
        revertFunc();
      }
    );
  }

  eventRender(arg: EventHandlerArg<'eventRender'>) {
    const props: {name: string, type: string, info: string, htmlTitle: string} = arg.event.extendedProps;
    if (props.htmlTitle) {
      if (arg.el.getElementsByClassName('fc-title').length > 0) {
        arg.el.getElementsByClassName('fc-title')[0].innerHTML = props.htmlTitle;
      }
      if (arg.el.getElementsByClassName('fc-list-item-title').length > 0) {
        arg.el.getElementsByClassName('fc-list-item-title')[0].innerHTML = props.htmlTitle;
      }
    }
    const element = $(arg.el);
    import('tooltipster').then(
      () => {
        element.tooltipster(
          {
            theme: 'tooltipster-shadow',
            delay: 100,
            trigger: 'hover',
            triggerOpen: {
              click: false,
              tap: false
            },
            triggerClose: {
              click: true,
              tap: true,
              scroll: true
            },
            side: 'top',
            trackOrigin: true
          }
        );
        const tooltip = element.tooltipster('instance');
        tooltip.content($(
          '<div class="tb-scheduler-tooltip-title">' + props.name + '</div>' +
          '<div class="tb-scheduler-tooltip-content"><b>' + this.translate.instant('scheduler.event-type') + ':</b> ' + props.type + '</div>' +
          '<div class="tb-scheduler-tooltip-content">' + props.info + '</div>'
        ));
      }
    );
  }

  updateCalendarEvents(schedulerEvents: Array<SchedulerEventWithCustomerInfo>) {
    this.schedulerEvents = schedulerEvents;
    if (this.calendarApi) {
      this.calendarApi.refetchEvents();
    }
  }

  eventSourceFunction(arg: {
    start: Date;
    end: Date;
    timeZone: string;
  },                  successCallback: (events: EventInput[]) => void, failureCallback: (error: EventSourceError) => void) {
    const events: EventInput[] = [];
    if (this.schedulerEvents && this.schedulerEvents.length) {
      const start = toMoment(arg.start, this.calendarApi);
      const end = toMoment(arg.end, this.calendarApi);
      const userZone = getUserZone();
      const rangeStart = start.local();
      const rangeEnd = end.local();
      this.schedulerEvents.forEach((event) => {
        const startOffset = userZone.utcOffset(event.schedule.startTime) * 60 * 1000;
        const eventStart = _moment(event.schedule.startTime - startOffset);
        let calendarEvent: EventInput;
        if (rangeEnd.isSameOrAfter(eventStart)) {
          if (event.schedule.repeat) {
            const endOffset = userZone.utcOffset(event.schedule.repeat.endsOn) * 60 * 1000;
            const repeatEndsOn = _moment(event.schedule.repeat.endsOn - endOffset);
            if (event.schedule.repeat.type === SchedulerRepeatType.TIMER) {
              calendarEvent = this.toCalendarEvent(event,
                eventStart,
                repeatEndsOn);
              events.push(calendarEvent);
            } else {
              let currentTime: _moment.Moment;
              let eventStartOffsetUnits = 0;
              if (rangeStart.isSameOrBefore(eventStart)) {
                currentTime = eventStart.clone();
              } else {
                switch (event.schedule.repeat.type) {
                  case SchedulerRepeatType.YEARLY:
                  case SchedulerRepeatType.MONTHLY:
                    const eventStartOffsetDuration = _moment.duration(rangeStart.diff(eventStart));
                    const offsetUnits = schedulerRepeatTypeToUnitMap.get(event.schedule.repeat.type);
                    eventStartOffsetUnits =
                      Math.ceil(eventStartOffsetDuration.as(offsetUnits));
                    currentTime = eventStart.clone().add(eventStartOffsetUnits, offsetUnits);
                    break;
                  default:
                    currentTime = rangeStart.clone();
                    currentTime.hours(eventStart.hours());
                    currentTime.minutes(eventStart.minutes());
                    currentTime.seconds(eventStart.seconds());
                }
              }
              let eventEnd;
              if (rangeEnd.isSameOrAfter(repeatEndsOn)) {
                eventEnd = repeatEndsOn.clone();
              } else {
                eventEnd = rangeEnd.clone();
              }
              while (currentTime.isBefore(eventEnd)) {
                const day = currentTime.day();
                if (event.schedule.repeat.type !== SchedulerRepeatType.WEEKLY ||
                  event.schedule.repeat.repeatOn.indexOf(day) !== -1) {
                  const currentEventStart = currentTime.clone();
                  calendarEvent = this.toCalendarEvent(event, currentEventStart);
                  events.push(calendarEvent);
                }
                switch (event.schedule.repeat.type) {
                  case SchedulerRepeatType.YEARLY:
                  case SchedulerRepeatType.MONTHLY:
                    eventStartOffsetUnits++;
                    currentTime = eventStart.clone()
                      .add(eventStartOffsetUnits, schedulerRepeatTypeToUnitMap.get(event.schedule.repeat.type));
                    break;
                  default:
                    currentTime.add(1, 'days');
                }
              }
            }
          } else if (rangeStart.isSameOrBefore(eventStart)) {
            calendarEvent = this.toCalendarEvent(event, eventStart);
            events.push(calendarEvent);
          }
        }
      });
      successCallback(events);
    } else {
      successCallback(events);
    }
  }

  private toCalendarEvent(event: SchedulerEventWithCustomerInfo, start: _moment.Moment, end?: _moment.Moment): EventInput {
    const title = `${event.name} - ${event.typeName}`;
    let htmlTitle = null;
    if (event.schedule.repeat && event.schedule.repeat.type === SchedulerRepeatType.TIMER) {
      const repeatInterval = this.translate.instant(schedulerTimeUnitRepeatTranslationMap.get(event.schedule.repeat.timeUnit),
        {count: event.schedule.repeat.repeatInterval});
      htmlTitle = ` <b>${repeatInterval}</b> ${title}`;
    }
    const calendarEvent: EventInput = {
      id: event.id.id,
      title,
      name: event.name,
      type: event.typeName,
      info: this.eventInfo(event),
      start: start.toDate(),
      end: end ? end.toDate() : null,
      htmlTitle
    };
    return calendarEvent;
  }

  private eventInfo(event: SchedulerEventWithCustomerInfo): string {
    let info = '';
    const startTime = event.schedule.startTime;
    if (!event.schedule.repeat) {
      const start = _moment.utc(startTime).local().format('MMM DD, YYYY, hh:mma');
      info += start;
      return info;
    } else {
      info += _moment.utc(startTime).local().format('hh:mma');
      info += '<br/>';
      info += this.translate.instant('scheduler.starting-from') + ' ' + _moment.utc(startTime).local().format('MMM DD, YYYY') + ', ';
      if (event.schedule.repeat.type === SchedulerRepeatType.DAILY) {
        info += this.translate.instant('scheduler.daily') + ', ';
      } else if (event.schedule.repeat.type === SchedulerRepeatType.MONTHLY) {
        info += this.translate.instant('scheduler.monthly') + ', ';
      } else if (event.schedule.repeat.type === SchedulerRepeatType.YEARLY) {
        info += this.translate.instant('scheduler.yearly') + ', ';
      } else if (event.schedule.repeat.type === SchedulerRepeatType.TIMER) {
          const repeatInterval = this.translate.instant(schedulerTimeUnitRepeatTranslationMap.get(event.schedule.repeat.timeUnit),
            {count: event.schedule.repeat.repeatInterval});
          info += repeatInterval + ', ';
      } else {
        info += this.translate.instant('scheduler.weekly') + ' ' + this.translate.instant('scheduler.on') + ' ';
        event.schedule.repeat.repeatOn.forEach((day) => {
          info += this.translate.instant(schedulerWeekday[day]) + ', ';
        });
      }
      info += this.translate.instant('scheduler.until') + ' ';
      info += _moment.utc(event.schedule.repeat.endsOn).local().format('MMM DD, YYYY');
      return info;
    }
  }

}

class SchedulerEventsDatasource implements DataSource<SchedulerEventWithCustomerInfo> {

  private entitiesSubject = new BehaviorSubject<SchedulerEventWithCustomerInfo[]>([]);
  private pageDataSubject = new BehaviorSubject<PageData<SchedulerEventWithCustomerInfo>>(emptyPageData<SchedulerEventWithCustomerInfo>());

  public pageData$ = this.pageDataSubject.asObservable();

  public selection = new SelectionModel<SchedulerEventWithCustomerInfo>(true, []);

  private allEntities: Observable<Array<SchedulerEventWithCustomerInfo>>;

  public dataLoading = true;

  constructor(private schedulerEventService: SchedulerEventService,
              private schedulerEventConfigTypes: {[eventType: string]: SchedulerEventConfigType}) {
  }

  connect(collectionViewer: CollectionViewer):
    Observable<SchedulerEventWithCustomerInfo[] | ReadonlyArray<SchedulerEventWithCustomerInfo>> {
    return this.entitiesSubject.asObservable();
  }

  disconnect(collectionViewer: CollectionViewer): void {
    this.entitiesSubject.complete();
    this.pageDataSubject.complete();
  }

  reset() {
    const pageData = emptyPageData<SchedulerEventWithCustomerInfo>();
    this.entitiesSubject.next(pageData.data);
    this.pageDataSubject.next(pageData);
  }

  loadEntities(pageLink: PageLink, eventType: string,
               reload: boolean = false): Observable<PageData<SchedulerEventWithCustomerInfo>> {
    this.dataLoading = true;
    if (reload) {
      this.allEntities = null;
    }
    const result = new ReplaySubject<PageData<SchedulerEventWithCustomerInfo>>();
    this.fetchEntities(eventType, pageLink).pipe(
      tap(() => {
        this.selection.clear();
      }),
      catchError(() => of([
        emptyPageData<SchedulerEventWithCustomerInfo>(),
        emptyPageData<SchedulerEventWithCustomerInfo>()
      ])),
    ).subscribe(
      (pageData) => {
        this.entitiesSubject.next(pageData[0].data);
        this.pageDataSubject.next(pageData[0]);
        result.next(pageData[1]);
        this.dataLoading = false;
      }
    );
    return result;
  }

  fetchEntities(eventType: string,
                pageLink: PageLink): Observable<Array<PageData<SchedulerEventWithCustomerInfo>>> {
    const allPageLinkData = new PageLink(Number.POSITIVE_INFINITY, 0, pageLink.textSearch);
    return this.getAllEntities(eventType).pipe(
      map((data) => allPageLinkData.filterData(data)),
      map((data) => [pageLink.filterData(data.data), data])
    );
  }

  getAllEntities(eventType: string): Observable<Array<SchedulerEventWithCustomerInfo>> {
    if (!this.allEntities) {
      this.allEntities = this.schedulerEventService.getSchedulerEvents(eventType).pipe(
        map((schedulerEvents) => {
          schedulerEvents.forEach((schedulerEvent) => {
            let typeName = schedulerEvent.type;
            if (this.schedulerEventConfigTypes[typeName]) {
              typeName = this.schedulerEventConfigTypes[typeName].name;
            }
            schedulerEvent.typeName = typeName;
          });
          return schedulerEvents;
        }),
        publishReplay(1),
        refCount()
      );
    }
    return this.allEntities;
  }

  isAllSelected(): Observable<boolean> {
    const numSelected = this.selection.selected.length;
    return this.entitiesSubject.pipe(
      map((entities) => numSelected === entities.length)
    );
  }

  isEmpty(): Observable<boolean> {
    return this.entitiesSubject.pipe(
      map((entities) => !entities.length)
    );
  }

  total(): Observable<number> {
    return this.pageDataSubject.pipe(
      map((pageData) => pageData.totalElements)
    );
  }

  masterToggle() {
    this.entitiesSubject.pipe(
      tap((entities) => {
        const numSelected = this.selection.selected.length;
        if (numSelected === entities.length) {
          this.selection.clear();
        } else {
          entities.forEach(row => {
            this.selection.select(row);
          });
        }
      }),
      take(1)
    ).subscribe();
  }
}
