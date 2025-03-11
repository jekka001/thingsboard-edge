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

import { FormattedData } from '@shared/models/widget.models';
import L from 'leaflet';
import { TbMapDatasource } from '@shared/models/widget/maps/map.models';
import { MatIconRegistry } from '@angular/material/icon';

// redeclare module, maintains compatibility with @types/leaflet
declare module 'leaflet' {
  interface MarkerOptions {
    tbMarkerData?: FormattedData<TbMapDatasource>;
  }

  interface TileLayer {
    _url: string;
    _getSubdomain(tilePoint: L.Coords): string;
    _globalTileRange: L.Bounds;
  }

  namespace TB {

    interface SidebarControlOptions extends ControlOptions {
      container: JQuery<HTMLElement>;
      paneWidth?: number;
    }

    class SidebarControl extends Control<SidebarControlOptions> {
      constructor(options: SidebarControlOptions);
      addPane(pane: JQuery<HTMLElement>, button: JQuery<HTMLElement>): this;
      togglePane(pane: JQuery<HTMLElement>, button: JQuery<HTMLElement>): void;
    }

    interface SidebarPaneControlOptions extends ControlOptions {
      sidebar: SidebarControl;
      uiClass: string;
      buttonTitle?: string;
      paneTitle: string;
    }

    class SidebarPaneControl<O extends SidebarPaneControlOptions> extends Control<O> {
      constructor(options: O);
      onAddPane(map: Map, button: JQuery<HTMLElement>, $ui: JQuery<HTMLElement>, toggle: (e: JQuery.MouseEventBase) => void);
    }

    interface LayerData {
      title: string;
      attributionPrefix?: string;
      layer: Layer;
      mini: Layer;
      onAdd?: () => void;
    }

    interface LayersControlOptions extends SidebarPaneControlOptions {
      layers: LayerData[];
    }

    class LayersControl extends SidebarPaneControl<LayersControlOptions> {
      constructor(options: LayersControlOptions);
    }

    interface DataLayer {
      toggleGroup(group: string): boolean;
    }

    interface GroupData {
      title: string;
      group: string;
      enabled: boolean;
      dataLayers: DataLayer[];
    }

    interface GroupsControlOptions extends SidebarPaneControlOptions {
      groups: GroupData[];
    }

    class GroupsControl extends SidebarPaneControl<GroupsControlOptions> {
      constructor(options: GroupsControlOptions);
    }

    interface TopToolbarButtonOptions {
      icon: string;
      color?: string;
      title: string;
    }

    class TopToolbarButton {
      constructor(options: TopToolbarButtonOptions, iconRegistry: MatIconRegistry);
      onClick(onClick: (e: MouseEvent, button: TopToolbarButton) => void): void;
      setActive(active: boolean): void;
      isActive(): boolean;
      setDisabled(disabled: boolean): void;
      isDisabled(): boolean;
    }

    interface TopToolbarControlOptions {
      mapElement: JQuery<HTMLElement>;
      iconRegistry: MatIconRegistry;
    }

    class TopToolbarControl {
      constructor(options: TopToolbarControlOptions);
      toolbarButton(options: TopToolbarButtonOptions): TopToolbarButton;
      setDisabled(disabled: boolean): void;
    }

    interface ToolbarButtonOptions {
      id: string;
      title: string;
      click: (e: MouseEvent, button: ToolbarButton) => void;
      iconClass: string;
      showText?: boolean;
    }

    class ToolbarButton {
      constructor(options: ToolbarButtonOptions);
      setActive(active: boolean): void;
      isActive(): boolean;
      setDisabled(disabled: boolean): void;
      isDisabled(): boolean;
    }

    class ToolbarControl extends Control<ControlOptions> {
      constructor(options: ControlOptions);
      toolbarButton(options: ToolbarButtonOptions): ToolbarButton;
    }

    interface BottomToolbarControlOptions {
      mapElement: JQuery<HTMLElement>;
      closeTitle: string;
      onClose: () => boolean;
    }

    class BottomToolbarControl {
      constructor(options: BottomToolbarControlOptions);
      getButton(id: string): ToolbarButton | undefined;
      open(buttons: ToolbarButtonOptions[], showCloseButton?: boolean): void;
      close(): void;
      container: HTMLElement;
    }

    function sidebar(options: SidebarControlOptions): SidebarControl;

    function sidebarPane<O extends SidebarPaneControlOptions>(options: O): SidebarPaneControl<O>;

    function layers(options: LayersControlOptions): LayersControl;

    function groups(options: GroupsControlOptions): GroupsControl;

    function topToolbar(options: TopToolbarControlOptions): TopToolbarControl;

    function toolbar(options: ControlOptions): ToolbarControl;

    function bottomToolbar(options: BottomToolbarControlOptions): BottomToolbarControl;

    namespace TileLayer {

      interface ChinaProvidersData {
        [provider: string]: {
          [type: string]: string;
          Subdomains: string;
        };
      }

      class ChinaProvider extends L.TileLayer {
        constructor(type: string, options?: TileLayerOptions);
      }
    }

    namespace tileLayer {
      function chinaProvider(type: string, options?: TileLayerOptions): TileLayer.ChinaProvider;
    }
  }
}
