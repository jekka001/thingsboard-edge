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

import * as tinycolor_ from 'tinycolor2';
import { mergeDeep } from '@core/utils';

const tinycolor = tinycolor_;

export interface MaterialColorItem {
  value: string;
  group: string;
  label: string;
  isDark: boolean;
}

export type ColorPalette = {[spectrum: string]: string};

export interface PaletteContrastInfo {
  contrastDefaultColor: 'light' | 'dark',
  contrastDarkColors: string[];
  contrastLightColors: string[];
  contrastStrongLightColors: string[];
}

export const materialColorPaletteContrastInfo: {[palette: string]: PaletteContrastInfo} = {
  red: {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 300 A100'.split(' '),
    contrastStrongLightColors: '400 500 600 700 A200 A400 A700'.split(' '),
    contrastLightColors: []
  },
  pink: {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 A100'.split(' '),
    contrastStrongLightColors: '500 600 A200 A400 A700'.split(' '),
    contrastLightColors: []
  },
  purple: {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 A100'.split(' '),
    contrastStrongLightColors: '300 400 A200 A400 A700'.split(' '),
    contrastLightColors: []
  },
  'deep-purple': {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 A100'.split(' '),
    contrastStrongLightColors: '300 400 A200'.split(' '),
    contrastLightColors: []
  },
  indigo: {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 A100'.split(' '),
    contrastStrongLightColors: '300 400 A200 A400'.split(' '),
    contrastLightColors: []
  },
  blue: {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 300 400 A100'.split(' '),
    contrastStrongLightColors: '500 600 700 A200 A400 A700'.split(' '),
    contrastLightColors: []
  },
  'light-blue': {
    contrastDefaultColor: 'dark',
    contrastLightColors: '600 700 800 900 A700'.split(' '),
    contrastStrongLightColors: '600 700 800 A700'.split(' '),
    contrastDarkColors: []
  },
  cyan: {
    contrastDefaultColor: 'dark',
    contrastLightColors: '700 800 900'.split(' '),
    contrastStrongLightColors: '700 800 900'.split(' '),
    contrastDarkColors: []
  },
  teal: {
    contrastDefaultColor: 'dark',
    contrastLightColors: '500 600 700 800 900'.split(' '),
    contrastStrongLightColors: '500 600 700'.split(' '),
    contrastDarkColors: []
  },
  green: {
    contrastDefaultColor: 'dark',
    contrastLightColors: '500 600 700 800 900'.split(' '),
    contrastStrongLightColors: '500 600 700'.split(' '),
    contrastDarkColors: []
  },
  'light-green': {
    contrastDefaultColor: 'dark',
    contrastLightColors: '700 800 900'.split(' '),
    contrastStrongLightColors: '700 800 900'.split(' '),
    contrastDarkColors: []
  },
  lime: {
    contrastDefaultColor: 'dark',
    contrastLightColors: '900'.split(' '),
    contrastStrongLightColors: '900'.split(' '),
    contrastDarkColors: []
  },
  yellow: {
    contrastDefaultColor: 'dark',
    contrastDarkColors: [],
    contrastLightColors: [],
    contrastStrongLightColors: []
  },
  amber: {
    contrastDefaultColor: 'dark',
    contrastStrongLightColors: [],
    contrastLightColors: [],
    contrastDarkColors: []
  },
  orange: {
    contrastDefaultColor: 'dark',
    contrastLightColors: '800 900'.split(' '),
    contrastStrongLightColors: '800 900'.split(' '),
    contrastDarkColors: []
  },
  'deep-orange': {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 300 400 A100 A200'.split(' '),
    contrastStrongLightColors: '500 600 700 800 900 A400 A700'.split(' '),
    contrastLightColors: []
  },
  brown: {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 A100 A200'.split(' '),
    contrastStrongLightColors: '300 400'.split(' '),
    contrastLightColors: []
  },
  grey: {
    contrastDefaultColor: 'dark',
    contrastLightColors: '600 700 800 900 A200 A400 A700'.split(' '),
    contrastDarkColors: [],
    contrastStrongLightColors: []
  },
  'blue-grey': {
    contrastDefaultColor: 'light',
    contrastDarkColors: '50 100 200 300 A100 A200'.split(' '),
    contrastStrongLightColors: '400 500 700'.split(' '),
    contrastLightColors: []
  }
}

export const materialColorPalette: {[palette: string]: ColorPalette} = {
  red: {
    50: '#ffebee',
    100: '#ffcdd2',
    200: '#ef9a9a',
    300: '#e57373',
    400: '#ef5350',
    500: '#f44336',
    600: '#e53935',
    700: '#d32f2f',
    800: '#c62828',
    900: '#b71c1c',
    A100: '#ff8a80',
    A200: '#ff5252',
    A400: '#ff1744',
    A700: '#d50000'
  },
  pink: {
    50: '#fce4ec',
    100: '#f8bbd0',
    200: '#f48fb1',
    300: '#f06292',
    400: '#ec407a',
    500: '#e91e63',
    600: '#d81b60',
    700: '#c2185b',
    800: '#ad1457',
    900: '#880e4f',
    A100: '#ff80ab',
    A200: '#ff4081',
    A400: '#f50057',
    A700: '#c51162'
  },
  purple: {
    50: '#f3e5f5',
    100: '#e1bee7',
    200: '#ce93d8',
    300: '#ba68c8',
    400: '#ab47bc',
    500: '#9c27b0',
    600: '#8e24aa',
    700: '#7b1fa2',
    800: '#6a1b9a',
    900: '#4a148c',
    A100: '#ea80fc',
    A200: '#e040fb',
    A400: '#d500f9',
    A700: '#aa00ff'
  },
  'deep-purple': {
    50: '#ede7f6',
    100: '#d1c4e9',
    200: '#b39ddb',
    300: '#9575cd',
    400: '#7e57c2',
    500: '#673ab7',
    600: '#5e35b1',
    700: '#512da8',
    800: '#4527a0',
    900: '#311b92',
    A100: '#b388ff',
    A200: '#7c4dff',
    A400: '#651fff',
    A700: '#6200ea'
  },
  indigo: {
    50: '#e8eaf6',
    100: '#c5cae9',
    200: '#9fa8da',
    300: '#7986cb',
    400: '#5c6bc0',
    500: '#3f51b5',
    600: '#3949ab',
    700: '#303f9f',
    800: '#283593',
    900: '#1a237e',
    A100: '#8c9eff',
    A200: '#536dfe',
    A400: '#3d5afe',
    A700: '#304ffe'
  },
  blue: {
    50: '#e3f2fd',
    100: '#bbdefb',
    200: '#90caf9',
    300: '#64b5f6',
    400: '#42a5f5',
    500: '#2196f3',
    600: '#1e88e5',
    700: '#1976d2',
    800: '#1565c0',
    900: '#0d47a1',
    A100: '#82b1ff',
    A200: '#448aff',
    A400: '#2979ff',
    A700: '#2962ff'
  },
  'light-blue': {
    50: '#e1f5fe',
    100: '#b3e5fc',
    200: '#81d4fa',
    300: '#4fc3f7',
    400: '#29b6f6',
    500: '#03a9f4',
    600: '#039be5',
    700: '#0288d1',
    800: '#0277bd',
    900: '#01579b',
    A100: '#80d8ff',
    A200: '#40c4ff',
    A400: '#00b0ff',
    A700: '#0091ea'
  },
  cyan: {
    50: '#e0f7fa',
    100: '#b2ebf2',
    200: '#80deea',
    300: '#4dd0e1',
    400: '#26c6da',
    500: '#00bcd4',
    600: '#00acc1',
    700: '#0097a7',
    800: '#00838f',
    900: '#006064',
    A100: '#84ffff',
    A200: '#18ffff',
    A400: '#00e5ff',
    A700: '#00b8d4'
  },
  teal: {
    50: '#e0f2f1',
    100: '#b2dfdb',
    200: '#80cbc4',
    300: '#4db6ac',
    400: '#26a69a',
    500: '#009688',
    600: '#00897b',
    700: '#00796b',
    800: '#00695c',
    900: '#004d40',
    A100: '#a7ffeb',
    A200: '#64ffda',
    A400: '#1de9b6',
    A700: '#00bfa5'
  },
  green: {
    50: '#e8f5e9',
    100: '#c8e6c9',
    200: '#a5d6a7',
    300: '#81c784',
    400: '#66bb6a',
    500: '#4caf50',
    600: '#43a047',
    700: '#388e3c',
    800: '#2e7d32',
    900: '#1b5e20',
    A100: '#b9f6ca',
    A200: '#69f0ae',
    A400: '#00e676',
    A700: '#00c853'
  },
  'light-green': {
    50: '#f1f8e9',
    100: '#dcedc8',
    200: '#c5e1a5',
    300: '#aed581',
    400: '#9ccc65',
    500: '#8bc34a',
    600: '#7cb342',
    700: '#689f38',
    800: '#558b2f',
    900: '#33691e',
    A100: '#ccff90',
    A200: '#b2ff59',
    A400: '#76ff03',
    A700: '#64dd17'
  },
  lime: {
    50: '#f9fbe7',
    100: '#f0f4c3',
    200: '#e6ee9c',
    300: '#dce775',
    400: '#d4e157',
    500: '#cddc39',
    600: '#c0ca33',
    700: '#afb42b',
    800: '#9e9d24',
    900: '#827717',
    A100: '#f4ff81',
    A200: '#eeff41',
    A400: '#c6ff00',
    A700: '#aeea00'
  },
  yellow: {
    50: '#fffde7',
    100: '#fff9c4',
    200: '#fff59d',
    300: '#fff176',
    400: '#ffee58',
    500: '#ffeb3b',
    600: '#fdd835',
    700: '#fbc02d',
    800: '#f9a825',
    900: '#f57f17',
    A100: '#ffff8d',
    A200: '#ffff00',
    A400: '#ffea00',
    A700: '#ffd600'
  },
  amber: {
    50: '#fff8e1',
    100: '#ffecb3',
    200: '#ffe082',
    300: '#ffd54f',
    400: '#ffca28',
    500: '#ffc107',
    600: '#ffb300',
    700: '#ffa000',
    800: '#ff8f00',
    900: '#ff6f00',
    A100: '#ffe57f',
    A200: '#ffd740',
    A400: '#ffc400',
    A700: '#ffab00'
  },
  orange: {
    50: '#fff3e0',
    100: '#ffe0b2',
    200: '#ffcc80',
    300: '#ffb74d',
    400: '#ffa726',
    500: '#ff9800',
    600: '#fb8c00',
    700: '#f57c00',
    800: '#ef6c00',
    900: '#e65100',
    A100: '#ffd180',
    A200: '#ffab40',
    A400: '#ff9100',
    A700: '#ff6d00'
  },
  'deep-orange': {
    50: '#fbe9e7',
    100: '#ffccbc',
    200: '#ffab91',
    300: '#ff8a65',
    400: '#ff7043',
    500: '#ff5722',
    600: '#f4511e',
    700: '#e64a19',
    800: '#d84315',
    900: '#bf360c',
    A100: '#ff9e80',
    A200: '#ff6e40',
    A400: '#ff3d00',
    A700: '#dd2c00'
  },
  brown: {
    50: '#efebe9',
    100: '#d7ccc8',
    200: '#bcaaa4',
    300: '#a1887f',
    400: '#8d6e63',
    500: '#795548',
    600: '#6d4c41',
    700: '#5d4037',
    800: '#4e342e',
    900: '#3e2723',
    A100: '#d7ccc8',
    A200: '#bcaaa4',
    A400: '#8d6e63',
    A700: '#5d4037'
  },
  grey: {
    50: '#fafafa',
    100: '#f5f5f5',
    200: '#eeeeee',
    300: '#e0e0e0',
    400: '#bdbdbd',
    500: '#9e9e9e',
    600: '#757575',
    700: '#616161',
    800: '#424242',
    900: '#212121',
    A100: '#ffffff',
    A200: '#000000',
    A400: '#303030',
    A700: '#616161'
  },
  'blue-grey': {
    50: '#eceff1',
    100: '#cfd8dc',
    200: '#b0bec5',
    300: '#90a4ae',
    400: '#78909c',
    500: '#607d8b',
    600: '#546e7a',
    700: '#455a64',
    800: '#37474f',
    900: '#263238',
    A100: '#cfd8dc',
    A200: '#b0bec5',
    A400: '#78909c',
    A700: '#455a64'
  }
};

export function extendDefaultPalette(existingPaletteName: string, palette: ColorPalette) {
  return extendPalette(materialColorPalette, existingPaletteName, palette);
}

export function extendPalette(paletteMap: {[palette: string]: ColorPalette}, paletteName: string, palette: ColorPalette) {
  const existingPalette = paletteMap[paletteName];
  return mergeDeep({}, existingPalette, palette);
}

const DARK_CONTRAST_COLOR = 'rgba(0,0,0,0.87)';
const LIGHT_CONTRAST_COLOR = 'rgba(255,255,255,0.87)';
const STRONG_LIGHT_CONTRAST_COLOR = 'rgb(255,255,255)';

export function getContrastColor(palette: string, hueName: string): string {
  const paletteContrastInfo = materialColorPaletteContrastInfo[palette];
  if (paletteContrastInfo.contrastDefaultColor === 'light') {
    if (paletteContrastInfo.contrastDarkColors.indexOf(hueName) > -1) {
      return DARK_CONTRAST_COLOR;
    } else {
      return paletteContrastInfo.contrastStrongLightColors.indexOf(hueName) > -1 ? STRONG_LIGHT_CONTRAST_COLOR
        : LIGHT_CONTRAST_COLOR;
    }
  } else {
    if (paletteContrastInfo.contrastLightColors.indexOf(hueName) > -1) {
      return paletteContrastInfo.contrastStrongLightColors.indexOf(hueName) > -1 ? STRONG_LIGHT_CONTRAST_COLOR
        : LIGHT_CONTRAST_COLOR;
    } else {
      return DARK_CONTRAST_COLOR;
    }
  }
}

export const materialColors = new Array<MaterialColorItem>();

const colorPalettes = ['blue', 'green', 'red', 'amber', 'blue-grey', 'purple', 'light-green',
  'indigo', 'pink', 'yellow', 'light-blue', 'orange', 'deep-purple', 'lime', 'teal', 'brown', 'cyan', 'deep-orange', 'grey'];
const colorSpectrum = ['500', 'A700', '600', '700', '800', '900', '300', '400', 'A200', 'A400'];

for (const key of Object.keys(materialColorPalette)) {
  const value = materialColorPalette[key];
  for (const label of Object.keys(value)) {
    if (colorSpectrum.indexOf(label) > -1) {
      const colorValue = value[label];
      const color = tinycolor(colorValue);
      const isDark = color.isDark();
      const colorItem = {
        value: color.toHexString(),
        group: key,
        label,
        isDark
      };
      materialColors.push(colorItem);
    }
  }
}

materialColors.sort((colorItem1, colorItem2) => {
  const spectrumIndex1 = colorSpectrum.indexOf(colorItem1.label);
  const spectrumIndex2 = colorSpectrum.indexOf(colorItem2.label);
  let result = spectrumIndex1 - spectrumIndex2;
  if (result === 0) {
    const paletteIndex1 = colorPalettes.indexOf(colorItem1.group);
    const paletteIndex2 = colorPalettes.indexOf(colorItem2.group);
    result = paletteIndex1 - paletteIndex2;
  }
  return result;
});
