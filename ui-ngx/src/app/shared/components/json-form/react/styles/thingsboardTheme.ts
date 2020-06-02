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

/*
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import indigo from '@material-ui/core/colors/indigo';
import deeepOrange from '@material-ui/core/colors/deepOrange';
import { ThemeOptions } from '@material-ui/core/styles/createMuiTheme';
import { PaletteOptions, SimplePaletteColorOptions } from '@material-ui/core/styles/createPalette';
import { mergeDeep } from '@core/utils';
import { ColorPalette } from '@shared/models/material.models';

const PRIMARY_COLOR = '#305680';
const SECONDARY_COLOR = '#527dad';
const HUE3_COLOR = '#a7c1de';

const tbIndigo = mergeDeep<any>({}, indigo, {
  500: PRIMARY_COLOR,
  600: SECONDARY_COLOR,
  700: PRIMARY_COLOR,
  A100: HUE3_COLOR
});

const thingsboardPalette: PaletteOptions = {
  primary: tbIndigo,
  secondary: deeepOrange,
  background: {
    default: '#eee'
  },
  type: 'light'
};

export default function createThingsboardTheme(primaryPalette: ColorPalette, accentPalette: ColorPalette): ThemeOptions {
  thingsboardPalette.primary = mergeDeep<any>({}, thingsboardPalette.primary, primaryPalette);
  thingsboardPalette.secondary = mergeDeep<any>({}, thingsboardPalette.secondary, accentPalette);
  (thingsboardPalette.secondary as SimplePaletteColorOptions).main = thingsboardPalette.secondary['500'];
  return {
    typography: {
      fontFamily: 'Roboto, \'Helvetica Neue\', sans-serif'
    },
    palette: thingsboardPalette
  };
}
