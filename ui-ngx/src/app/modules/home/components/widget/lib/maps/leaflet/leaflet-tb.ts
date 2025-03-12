///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import L, { TB } from 'leaflet';
import { guid, isNotEmptyStr } from '@core/utils';
import 'leaflet-providers';
import '@maplibre/maplibre-gl-leaflet';
import '@geoman-io/leaflet-geoman-free';
import 'leaflet.markercluster';
import { MatIconRegistry } from '@angular/material/icon';
import { isSvgIcon, splitIconName } from '@shared/models/icon.models';
import { catchError, take } from 'rxjs/operators';
import { of } from 'rxjs';

L.MarkerCluster = L.MarkerCluster.mergeOptions({ pmIgnore: true });

class SidebarControl extends L.Control<TB.SidebarControlOptions> {

  private readonly sidebar: JQuery<HTMLElement>;

  private current = $();
  private currentButton = $();

  private map: L.Map;

  private buttonContainer: JQuery<HTMLElement>;

  constructor(options: TB.SidebarControlOptions) {
    super(options);
    this.sidebar = $('<div class="tb-map-sidebar"></div>');
    this.options.container.append(this.sidebar);
    const position = options?.position || 'topleft';
    if (['topleft', 'bottomleft'].includes(position)) {
      this.options.container.addClass('tb-sidebar-left');
    } else {
      this.options.container.addClass('tb-sidebar-right');
    }
  }

  addPane(pane: JQuery<HTMLElement>, button: JQuery<HTMLElement>): this {
    pane.hide().appendTo(this.sidebar);
    button.appendTo(this.buttonContainer);
    return this;
  }

  togglePane(pane: JQuery<HTMLElement>, button: JQuery<HTMLElement>) {
    const paneWidth = this.options?.paneWidth || 220;
    const position = this.options?.position || 'topleft';

    this.current.hide().trigger('hide');
    this.currentButton.removeClass('active');


    if (this.current === pane) {
      if (['topleft', 'bottomleft'].includes(position)) {
        this.map.panBy([-paneWidth, 0], { animate: false });
      }
      this.sidebar.hide();
      this.current = this.currentButton = $();
    } else {
      this.sidebar.show();
      if (['topleft', 'bottomleft'].includes(position) && !this.current.length) {
        this.map.panBy([paneWidth, 0], { animate: false });
      }
      this.current = pane;
      this.currentButton = button || $();
    }
    this.current.show().trigger('show');
    this.currentButton.addClass('active');
    this.map.invalidateSize({ pan: false, animate: false });
  }

  onAdd(map: L.Map): HTMLElement {
    this.buttonContainer = $("<div>")
    .attr('class', 'leaflet-bar');
    return this.buttonContainer[0];
  }

  addTo(map: L.Map): this {
    this.map = map;
    return super.addTo(map);
  }
}

class SidebarPaneControl<O extends TB.SidebarPaneControlOptions> extends L.Control<O> {

  private button: JQuery<HTMLElement>;
  private $ui: JQuery<HTMLElement>;

  constructor(options: O) {
    super(options);
  }

  addTo(map: L.Map): this {

    this.button = $("<a>")
    .attr('class', 'tb-control-button')
    .attr('href', '#')
    .attr('role', 'button')
    .html('<div class="' + this.options.uiClass + '"></div>')
    .on('click', (e) => {
      this.toggle(e);
    });
    if (this.options.buttonTitle) {
      this.button.attr('title', this.options.buttonTitle);
    }

    this.$ui = $('<div>')
        .attr('class', this.options.uiClass);

    $('<div class="tb-layers-title-container">')
    .appendTo(this.$ui)
    .append($('<div class="tb-layers-title">')
    .text(this.options.paneTitle))
    .append($('<div>')
    .append($('<button type="button" class="tb-button-close mdc-icon-button mat-mdc-icon-button">' +
      '<span class="mat-mdc-button-persistent-ripple mdc-icon-button__ripple"></span>' +
      '<span class="material-icons">close</span>' +
      '</button>')
    .attr('aria-label', 'Close')
    .bind('click', (e) => {
      this.toggle(e);
    })));

    this.options.sidebar.addPane(this.$ui, this.button);

    this.onAddPane(map, this.button, this.$ui, (e) => {
      this.toggle(e);
    });

    return this;
  }

  public onAddPane(map: L.Map, button: JQuery<HTMLElement>, $ui: JQuery<HTMLElement>, toggle: (e: JQuery.MouseEventBase) => void) {}

  private toggle(e: JQuery.MouseEventBase) {
    e.stopPropagation();
    e.preventDefault();
    if (!this.button.hasClass("disabled")) {
      this.options.sidebar.togglePane(this.$ui, this.button);
    }
  }
}

class LayersControl extends SidebarPaneControl<TB.LayersControlOptions> {
  constructor(options: TB.LayersControlOptions) {
    super(options);
  }

  public onAddPane(map: L.Map, button: JQuery<HTMLElement>, $ui: JQuery<HTMLElement>, toggle: (e: JQuery.MouseEventBase) => void) {
    const paneId = guid();
    const layers = this.options.layers;
    const baseSection = $("<div>")
    .attr('class', 'tb-layers-container')
    .appendTo($ui);

    layers.forEach((layerData, i) => {
      const id = `map-ui-layer-${paneId}-${i}`;
      const buttonContainer = $('<div class="tb-layer-card">')
      .appendTo(baseSection);
      const mapContainer = $('<div class="tb-layer-map">')
      .appendTo(buttonContainer);
      const input = $('<input type="radio" class="tb-layer-button" name="layer">')
      .prop('id', id)
      .prop('checked', map.hasLayer(layerData.layer))
      .appendTo(buttonContainer);

      const item = $('<label class="tb-layer-label">')
      .prop('for', id)
      .append($('<span>').append(layerData.title))
      .appendTo(buttonContainer);

      map.whenReady(() => {

        const miniMap = L.map(mapContainer[0], { attributionControl: false, zoomControl: false, keyboard: false })
        .addLayer(layerData.mini);

        miniMap.dragging.disable();
        miniMap.touchZoom.disable();
        miniMap.doubleClickZoom.disable();
        miniMap.scrollWheelZoom.disable();

        const moved = () => {
          miniMap.setView(map.getCenter(), Math.max(map.getZoom() - 2, 0));
        };

        const shown = () => {
          miniMap.invalidateSize();
          miniMap.setView(map.getCenter(), Math.max(map.getZoom() - 2, 0), { animate: false });
          map.on('moveend', moved);
        };

        $ui.on('show', shown);
        $ui.on('hide', () => {
          map.off('moveend', moved);
        })
      });


      input.on('click', (e: JQuery.MouseEventBase) => {
        e.stopPropagation();
        if (!map.hasLayer(layerData.layer)) {
          map.addLayer(layerData.layer);
          map.attributionControl.setPrefix(layerData.attributionPrefix);
          if (layerData.onAdd) {
            layerData.onAdd();
          }
          layers.forEach((other) => {
            if (other.layer !== layerData.layer) {
              map.removeLayer(other.layer);
            }
          });
          map.fire('baselayerchange', { layer: layerData.layer });
        }
      });

      item.on('dblclick', (e) => {
        toggle(e);
      });

      map.on('layeradd layerremove', function () {
        input.prop('checked', map.hasLayer(layerData.layer));
      });
    });
  }
}

class GroupsControl extends SidebarPaneControl<TB.GroupsControlOptions> {
  constructor(options: TB.GroupsControlOptions) {
    super(options);
  }

  public onAddPane(map: L.Map, button: JQuery<HTMLElement>, $ui: JQuery<HTMLElement>, toggle: (e: JQuery.MouseEventBase) => void) {
    const paneId = guid();
    const groups = this.options.groups;
    const baseSection = $("<div>")
    .attr('class', 'tb-layers-container')
    .appendTo($ui);

    groups.forEach((groupData, i) => {
      const id = `map-group-layer-${paneId}-${i}`;
      const checkBoxContainer = $('<div class="tb-group-checkbox">')
      .appendTo(baseSection);
      const input = $('<input type="checkbox" class="tb-group-button" name="group">')
      .prop('id', id)
      .prop('checked', groupData.enabled)
      .appendTo(checkBoxContainer);

      $('<label class="tb-group-label">')
      .prop('title', groupData.title)
      .prop('for', id)
      .append($('<span>').append(groupData.title))
      .appendTo(checkBoxContainer);
      input.on('click', (e: JQuery.MouseEventBase) => {
        e.stopPropagation();
        groupData.enabled = !groupData.enabled;
        let changed = false;
        groupData.dataLayers.forEach(
          (dl) => {
            changed = dl.toggleGroup(groupData.group) || changed;
          }
        );
        if (changed) {
          map.fire('layergroupchange', {group: groupData});
        }
      });

      map.on('layeradd layerremove', function () {
        input.prop('checked', groupData.enabled);
      });
    });
  }
}

class TopToolbarButton {
  private readonly button: JQuery<HTMLElement>;
  private active = false;
  private disabled = false;
  private _onClick: (e: MouseEvent, button: TopToolbarButton) => void;

  constructor(private readonly options: TB.TopToolbarButtonOptions,
              private readonly iconRegistry: MatIconRegistry) {
    const iconElement = $('<div class="tb-control-button-icon"></div>');
    const setIcon = isNotEmptyStr(this.options.icon);
    const setTitle = isNotEmptyStr(this.options.title);
    this.button = $("<a>")
    .attr('class', 'tb-control-button tb-control-text-button')
    .attr('href', '#')
    .attr('role', 'button');
    if (setIcon) {
      this.button.append(iconElement);
      this.loadIcon(iconElement);
    }
    if (setTitle) {
      this.button.append(`<div class="tb-control-text">${this.options.title}</div>`);
    }
    this.button.css('--tb-map-control-color', this.options.color);
    this.button.css('--tb-map-control-active-color', this.options.color);
    this.button.css('--tb-map-control-hover-background-color', this.options.color);
    if (setIcon && !setTitle) {
      this.button.css('padding', 0);
    } else if (!setIcon && setTitle) {
      this.button.css('padding-left', '14px');
    }
    this.button.on('click', (e) => {
      e.stopPropagation();
      e.preventDefault();
      if (this._onClick) {
        this._onClick(e.originalEvent, this);
      }
    });
  }

  onClick(onClick: (e: MouseEvent, button: TopToolbarButton) => void): void {
   this._onClick = onClick;
  }

  private loadIcon(iconElement: JQuery<HTMLElement>) {
    iconElement.addClass(this.iconRegistry.getDefaultFontSetClass());

    if (!isSvgIcon(this.options.icon)) {
      iconElement.addClass('material-icon-font');
      iconElement.text(this.options.icon);
      return;
    }

    const [namespace, iconName] = splitIconName(this.options.icon);
    this.iconRegistry
      .getNamedSvgIcon(iconName, namespace)
      .pipe(
        take(1),
        catchError((err: Error) => {
          console.log(`Error retrieving icon ${namespace}:${iconName}! ${err.message}`)
          return of(null);
        })
      )
      .subscribe({
        next: (svg) => {
          iconElement.append(svg);
          svg.style.height = '24px';
          svg.style.width = '24px';
        }
      });
  }

  setActive(active: boolean): void {
    if (this.active !== active) {
      this.active = active;
      if (this.active) {
        L.DomUtil.addClass(this.button[0], 'active');
      } else {
        L.DomUtil.removeClass(this.button[0], 'active');
      }
    }
  }

  isActive(): boolean {
    return this.active;
  }

  setDisabled(disabled: boolean): void {
    if (this.disabled !== disabled) {
      this.disabled = disabled;
      if (this.disabled) {
        L.DomUtil.addClass(this.button[0], 'leaflet-disabled');
        this.button[0].setAttribute('aria-disabled', 'true');
      } else {
        L.DomUtil.removeClass(this.button[0], 'leaflet-disabled');
        this.button[0].setAttribute('aria-disabled', 'false');
      }
    }
  }

  isDisabled(): boolean {
    return this.disabled;
  }

  getButtonElement(): JQuery<HTMLElement> {
    return this.button;
  }
}

class ToolbarButton {
  private readonly id: string;
  private readonly button: JQuery<HTMLElement>;
  private active = false;
  private disabled = false;

  constructor(private readonly options: TB.ToolbarButtonOptions) {
    this.id = options.id;
    const buttonText = this.options.showText ? this.options.title : null;
    this.button = $("<a>")
    .attr('class', 'tb-control-button')
    .attr('href', '#')
    .attr('role', 'button')
    .html('<div class="'+this.options.iconClass+'"></div>' + (buttonText ? `<div class="tb-control-text">${buttonText}</div>` : ''));
    if (this.options.showText) {
      L.DomUtil.addClass(this.button[0], 'tb-control-text-button');
    } else {
      this.button.attr('title', this.options.title);
    }

    this.button.on('click', (e) => {
      e.stopPropagation();
      e.preventDefault();
      this.options.click(e.originalEvent, this);
    });
  }

  setActive(active: boolean): void {
    if (this.active !== active) {
      this.active = active;
      if (this.active) {
        L.DomUtil.addClass(this.button[0], 'active');
      } else {
        L.DomUtil.removeClass(this.button[0], 'active');
      }
    }
  }

  isActive(): boolean {
    return this.active;
  }

  setDisabled(disabled: boolean): void {
    if (this.disabled !== disabled) {
      this.disabled = disabled;
      if (this.disabled) {
        L.DomUtil.addClass(this.button[0], 'leaflet-disabled');
        this.button[0].setAttribute('aria-disabled', 'true');
      } else {
        L.DomUtil.removeClass(this.button[0], 'leaflet-disabled');
        this.button[0].setAttribute('aria-disabled', 'false');
      }
    }
  }

  isDisabled(): boolean {
    return this.disabled;
  }

  getId(): string {
    return this.id;
  }

  getButtonElement(): JQuery<HTMLElement> {
    return this.button;
  }
}

class TopToolbarControl {

  private readonly toolbarElement: JQuery<HTMLElement>;
  private buttons: Array<TopToolbarButton> = [];

  constructor(private readonly options: TB.TopToolbarControlOptions) {
    const controlContainer = $('.leaflet-control-container', this.options.mapElement);
    this.toolbarElement = $('<div class="tb-map-top-toolbar leaflet-top"></div>');
    this.toolbarElement.appendTo(controlContainer);
  }

  toolbarButton(options: TB.TopToolbarButtonOptions): TopToolbarButton {
    const button = new TopToolbarButton(options, this.options.iconRegistry);
    const buttonContainer = $('<div class="leaflet-bar leaflet-control"></div>');
    button.getButtonElement().appendTo(buttonContainer);
    buttonContainer.appendTo(this.toolbarElement);
    this.buttons.push(button);
    return button;
  }

  setDisabled(disabled: boolean): void {
    this.buttons.forEach(button => {
      if (!button.isActive()) {
        button.setDisabled(disabled);
      }
    });
  }
}

class ToolbarControl extends L.Control<L.ControlOptions> {

  private buttonContainer: JQuery<HTMLElement>;

  constructor(options: L.ControlOptions) {
    super(options);
  }

  toolbarButton(options: TB.ToolbarButtonOptions): ToolbarButton {
    const button = new ToolbarButton(options);
    button.getButtonElement().appendTo(this.buttonContainer);
    return button;
  }

  onAdd(map: L.Map): HTMLElement {
    this.buttonContainer = $("<div>")
    .attr('class', 'leaflet-bar');
    return this.buttonContainer[0];
  }

  addTo(map: L.Map): this {
    return super.addTo(map);
  }

}

class BottomToolbarControl {

  private readonly buttonContainer: JQuery<HTMLElement>;
  private toolbarButtons: ToolbarButton[] = [];

  container: HTMLElement;

  constructor(private readonly options: TB.BottomToolbarControlOptions) {
    const controlContainer = $('.leaflet-control-container', options.mapElement);
    const toolbar = $('<div class="tb-map-bottom-toolbar leaflet-bottom"></div>');
    toolbar.appendTo(controlContainer);
    this.buttonContainer = $('<div class="leaflet-bar leaflet-control"></div>');
    this.buttonContainer.appendTo(toolbar);
    this.container = this.buttonContainer[0];
  }

  getButton(id: string): ToolbarButton {
    return this.toolbarButtons.find(b => b.getId() === id);
  }

  open(buttons: TB.ToolbarButtonOptions[], showCloseButton = true): void {

    this.toolbarButtons.length = 0;

    buttons.forEach(buttonOption => {
      const button = new ToolbarButton(buttonOption);
      this.toolbarButtons.push(button);
      button.getButtonElement().appendTo(this.container);
    });

    if (showCloseButton) {
      const closeButton = $("<a>")
      .attr('class', 'tb-control-button')
      .attr('href', '#')
      .attr('role', 'button')
      .attr('title', this.options.closeTitle)
      .html('<div class="tb-close"></div>');

      closeButton.on('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        this.close();
      });
      closeButton.appendTo(this.buttonContainer);
    }
  }

  close(): void {
    if (this.options.onClose) {
      if (this.options.onClose()) {
        this.buttonContainer.empty();
      }
    } else {
      this.buttonContainer.empty();
    }
  }

}

const sidebar = (options: TB.SidebarControlOptions): SidebarControl => {
  return new SidebarControl(options);
}

const sidebarPane = <O extends TB.SidebarPaneControlOptions>(options: O): SidebarPaneControl<O> => {
  return new SidebarPaneControl(options);
}

const layers = (options: TB.LayersControlOptions): LayersControl => {
  return new LayersControl(options);
}

const groups = (options: TB.GroupsControlOptions): GroupsControl => {
  return new GroupsControl(options);
}

const topToolbar = (options: TB.TopToolbarControlOptions): TopToolbarControl => {
  return new TopToolbarControl(options);
}

const toolbar = (options: L.ControlOptions): ToolbarControl => {
  return new ToolbarControl(options);
}

const bottomToolbar = (options: TB.BottomToolbarControlOptions): BottomToolbarControl => {
  return new BottomToolbarControl(options);
}

class ChinaProvider extends L.TileLayer {

  static chinaProviders: L.TB.TileLayer.ChinaProvidersData = {
    Tencent: {
      Normal: "//rt{s}.map.gtimg.com/tile?z={z}&x={x}&y={-y}&type=vector&styleid=3",
      Satellite: "//p{s}.map.gtimg.com/sateTiles/{z}/{sx}/{sy}/{x}_{-y}.jpg",
      Terrain: "//p{s}.map.gtimg.com/demTiles/{z}/{sx}/{sy}/{x}_{-y}.jpg",
      Subdomains: '0123',
    }
  };

  constructor(type: string, options?: L.TileLayerOptions) {
    options = options || {};

    const parts = type.split('.');
    const providerName = parts[0];
    const mapName = parts[1];

    const url = ChinaProvider.chinaProviders[providerName][mapName];
    options.subdomains = ChinaProvider.chinaProviders[providerName].Subdomains;

    super(url, options);
  }

  getTileUrl(coords: L.Coords): string {
    const data = {
      s: this._getSubdomain(coords),
      x: coords.x,
      y: coords.y,
      z: this._getZoomForUrl(),
      sx: null,
      sy: null
    };
    if (this._map && !this._map.options.crs.infinite) {
      const invertedY = this._globalTileRange.max.y - coords.y;
      if (this.options.tms) {
        data['y'] = invertedY;
      }
      data['-y'] = invertedY;
    }
    data.sx = data.x >> 4;
    data.sy = (( 1 << data.z) - data.y) >> 4;
    return L.Util.template(this._url, L.Util.extend(data, this.options));
  }
}

const chinaProvider = (type: string, options?: L.TileLayerOptions): ChinaProvider => {
  return new ChinaProvider(type, options);
}

L.TB = L.TB || {
  SidebarControl,
  SidebarPaneControl,
  LayersControl,
  GroupsControl,
  TopToolbarButton,
  TopToolbarControl,
  ToolbarButton,
  ToolbarControl,
  BottomToolbarControl,
  sidebar,
  sidebarPane,
  layers,
  groups,
  topToolbar,
  toolbar,
  bottomToolbar,
  TileLayer: {
    ChinaProvider
  },
  tileLayer: {
    chinaProvider
  }
}
