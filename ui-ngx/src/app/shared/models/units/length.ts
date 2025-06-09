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

import { TbMeasure, TbMeasureUnits } from '@shared/models/unit.models';

export type LengthUnits = LengthMetricUnits | LengthImperialUnits;

export type LengthMetricUnits = 'nm' | 'μm' | 'mm' | 'cm' | 'dm' | 'm' | 'km' | 'angstrom';
export type LengthImperialUnits =
  | 'in'
  | 'yd'
  | 'ft-us'
  | 'ft'
  | 'fathom'
  | 'mi'
  | 'nmi'
  | 'pouce'
  | 'thou'
  | 'barleycorn'
  | 'hand'
  | 'ch'
  | 'fur'
  | 'league'
  | 'link'
  | 'rod'
  | 'cable'
  | 'AU';

const METRIC: TbMeasureUnits<LengthMetricUnits> = {
  ratio: 3.28084,
  units: {
    nm: {
      name: 'unit.nanometer',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'nanoscale', 'atomic scale', 'molecular scale'],
      to_anchor: 1e-9,
    },
    μm: {
      name: 'unit.micrometer',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'microns'],
      to_anchor: 1e-6,
    },
    mm: {
      name: 'unit.millimeter',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'rainfall', 'precipitation', 'displacement', 'position', 'movement', 'transition'],
      to_anchor: 1e-3,
    },
    cm: {
      name: 'unit.centimeter',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'rainfall', 'precipitation', 'displacement', 'position', 'movement', 'transition'],
      to_anchor: 1e-2,
    },
    dm: {
      name: 'unit.decimeter',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth'],
      to_anchor: 1e-1,
    },
    m: {
      name: 'unit.meter',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth'],
      to_anchor: 1,
    },
    km: {
      name: 'unit.kilometer',
      tags: ['distance', 'height', 'width', 'gap', 'depth'],
      to_anchor: 1e3,
    },
    angstrom: {
      name: 'unit.angstrom',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'atomic scale', 'atomic distance', 'nanoscale'],
      to_anchor: 1e-10,
    },
  },
};

const IMPERIAL: TbMeasureUnits<LengthImperialUnits> = {
  ratio: 1 / 3.28084,
  units: {
    in: {
      name: 'unit.inch',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth'],
      to_anchor: 1 / 12,
    },
    yd: {
      name: 'unit.yard',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth'],
      to_anchor: 3,
    },
    'ft-us': {
      name: 'unit.foot-us',
      tags: ['us survey foot', 'us survey feet', 'ft-us', 'surveying'],
      to_anchor: 1.000002,
    },
    ft: {
      name: 'unit.foot',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'feet'],
      to_anchor: 1,
    },
    fathom: {
      name: 'unit.fathom',
      tags: ['depth', 'nautical measurement'],
      to_anchor: 6,
    },
    mi: {
      name: 'unit.mile',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth'],
      to_anchor: 5280,
    },
    nmi: {
      name: 'unit.nautical-mile',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'nautical mile'],
      to_anchor: 6076.12,
    },
    pouce: {
      name: 'unit.paris-inch',
      tags: ['level', 'height', 'distance', 'width', 'gap', 'depth', 'nautical mile'],
      to_anchor: 1.0657,
    },
    thou: {
      name: 'unit.thou',
      tags: ['measurement'],
      to_anchor: 0.001 / 12,
    },
    barleycorn: {
      name: 'unit.barleycorn',
      tags: ['shoe size'],
      to_anchor: 1 / 36,
    },
    hand: {
      name: 'unit.hand',
      tags: ['horse measurement'],
      to_anchor: 4 / 12,
    },
    ch: {
      name: 'unit.chain',
      tags: ['land surveying'],
      to_anchor: 66,
    },
    fur: {
      name: 'unit.furlong',
      tags: ['land surveying'],
      to_anchor: 660,
    },
    league: {
      name: 'unit.league',
      tags: ['historical measurement'],
      to_anchor: 3 * 5280,
    },
    link: {
      name: 'unit.link',
      tags: ['land surveying'],
      to_anchor: 0.66,
    },
    rod: {
      name: 'unit.rod',
      tags: ['land surveying'],
      to_anchor: 16.5,
    },
    cable: {
      name: 'unit.cable',
      tags: ['distance', 'nautical measurement'],
      to_anchor: 600,
    },
    AU: {
      name: 'unit.astronomical-unit',
      tags: ['distance', 'celestial bodies', 'solar system'],
      to_anchor: 149597870700 * 3.28084,
    },
  },
};

const measure: TbMeasure<LengthUnits> = {
  METRIC,
  IMPERIAL
};

export default measure;
