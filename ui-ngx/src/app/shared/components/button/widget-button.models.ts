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

import { cssUnit } from '@shared/models/widget-settings.models';
import tinycolor from 'tinycolor2';

const defaultMainColor = '#3F52DD';
const defaultBackgroundColor = '#FFFFFF';

export const defaultMainColorDisabled = 'rgba(0, 0, 0, 0.38)';
export const defaultBackgroundColorDisabled = 'rgba(0, 0, 0, 0.03)';

const defaultBoxShadowColor = 'rgba(0, 0, 0, 0.08)';
const defaultDisabledBoxShadowColor = 'rgba(0, 0, 0, 0)';

export enum WidgetButtonType {
  outlined = 'outlined',
  filled = 'filled',
  underlined = 'underlined',
  basic = 'basic'
}

export const widgetButtonTypes = Object.keys(WidgetButtonType) as WidgetButtonType[];

export const widgetButtonTypeTranslations = new Map<WidgetButtonType, string>(
  [
    [WidgetButtonType.outlined, 'widgets.button.outlined'],
    [WidgetButtonType.filled, 'widgets.button.filled'],
    [WidgetButtonType.underlined, 'widgets.button.underlined'],
    [WidgetButtonType.basic, 'widgets.button.basic']
  ]
);

export const widgetButtonTypeImages = new Map<WidgetButtonType, string>(
  [
    [WidgetButtonType.outlined, 'assets/widget/button/outlined.svg'],
    [WidgetButtonType.filled, 'assets/widget/button/filled.svg'],
    [WidgetButtonType.underlined, 'assets/widget/button/underlined.svg'],
    [WidgetButtonType.basic, 'assets/widget/button/basic.svg']
  ]
);

export enum WidgetButtonState {
  enabled = 'enabled',
  hovered = 'hovered',
  pressed = 'pressed',
  activated = 'activated',
  disabled = 'disabled'
}

export const widgetButtonStates = Object.keys(WidgetButtonState) as WidgetButtonState[];

export const widgetButtonStatesTranslations = new Map<WidgetButtonState, string>(
  [
    [WidgetButtonState.enabled, 'widgets.button-state.enabled'],
    [WidgetButtonState.hovered, 'widgets.button-state.hovered'],
    [WidgetButtonState.pressed, 'widgets.button-state.pressed'],
    [WidgetButtonState.activated, 'widgets.button-state.activated'],
    [WidgetButtonState.disabled, 'widgets.button-state.disabled']
  ]
);

export interface WidgetButtonCustomStyle {
  overrideMainColor?: boolean;
  mainColor?: string;
  overrideBackgroundColor?: boolean;
  backgroundColor?: string;
  overrideDropShadow?: boolean;
  dropShadow?: boolean;
}

export type WidgetButtonCustomStyles = Record<WidgetButtonState, WidgetButtonCustomStyle>;

export interface WidgetButtonAppearance {
  type: WidgetButtonType;
  autoScale: boolean;
  showLabel: boolean;
  label: string;
  showIcon: boolean;
  icon: string;
  iconSize: number;
  iconSizeUnit: cssUnit;
  mainColor: string;
  backgroundColor: string;
  customStyle: WidgetButtonCustomStyles;
}

export const widgetButtonDefaultAppearance: WidgetButtonAppearance = {
  type: WidgetButtonType.outlined,
  autoScale: true,
  showLabel: true,
  label: 'Button',
  showIcon: true,
  icon: 'home',
  iconSize: 24,
  iconSizeUnit: 'px',
  mainColor: defaultMainColor,
  backgroundColor: defaultBackgroundColor,
  customStyle: {
    enabled: null,
    hovered: null,
    pressed: null,
    activated: null,
    disabled: null
  }
};

const mainColorVarPrefix = '--tb-widget-button-main-color-';
const backgroundColorVarPrefix = '--tb-widget-button-background-color-';
const boxShadowColorVarPrefix = '--tb-widget-button-box-shadow-color-';

abstract class ButtonStateCssGenerator {

  constructor() {}

  public generateStateCss(appearance: WidgetButtonAppearance): string {
    let mainColor = this.getMainColor(appearance);
    let backgroundColor = this.getBackgroundColor(appearance);
    const shadowEnabledByDefault = appearance.type !== WidgetButtonType.basic;
    let shadowColor = shadowEnabledByDefault ? defaultBoxShadowColor : defaultDisabledBoxShadowColor;
    const stateCustomStyle = appearance.customStyle[this.state];
    if (stateCustomStyle?.overrideMainColor && stateCustomStyle?.mainColor) {
      mainColor = stateCustomStyle.mainColor;
    }
    if (stateCustomStyle?.overrideBackgroundColor && stateCustomStyle?.backgroundColor) {
      backgroundColor = stateCustomStyle.backgroundColor;
    }
    if (stateCustomStyle?.overrideDropShadow) {
      shadowColor = !!stateCustomStyle.dropShadow ? defaultBoxShadowColor : defaultDisabledBoxShadowColor;
    }

    let css = `${mainColorVarPrefix}${this.state}: ${mainColor};\n`+
                     `${backgroundColorVarPrefix}${this.state}: ${backgroundColor};\n`+
                     `${boxShadowColorVarPrefix}${this.state}: ${shadowColor};`;
    const additionalCss = this.generateAdditionalStateCss(mainColor, backgroundColor);
    if (additionalCss) {
      css += `\n${additionalCss}`;
    }
    return css;
  }

  protected abstract get state(): WidgetButtonState;

  protected getMainColor(appearance: WidgetButtonAppearance): string {
    return appearance.mainColor || defaultMainColor;
  }

  protected getBackgroundColor(appearance: WidgetButtonAppearance): string {
    return appearance.backgroundColor || defaultBackgroundColor;
  }

  protected generateAdditionalStateCss(_mainColor: string, _backgroundColor: string): string {
    return null;
  }
}

class EnabledButtonStateCssGenerator extends ButtonStateCssGenerator {

  protected get state(): WidgetButtonState {
    return WidgetButtonState.enabled;
  }
}

class HoveredButtonStateCssGenerator extends ButtonStateCssGenerator {

  protected get state(): WidgetButtonState {
    return WidgetButtonState.hovered;
  }

  protected generateAdditionalStateCss(mainColor: string): string {
    const mainColorHoveredFilled = darkenColor(mainColor, 6);
    return `--tb-widget-button-main-color-hovered-filled: ${mainColorHoveredFilled};`;
  }
}

class PressedButtonStateCssGenerator extends ButtonStateCssGenerator {

  protected get state(): WidgetButtonState {
    return WidgetButtonState.pressed;
  }

  protected generateAdditionalStateCss(mainColor: string): string {
    const mainColorPressedFilled = darkenColor(mainColor, 12);
    const mainColorInstance = tinycolor(mainColor);
    const mainColorPressedRipple = mainColorInstance.setAlpha(mainColorInstance.getAlpha() * 0.1).toRgbString();
    const mainColorPressedRippleFilled = darkenColor(mainColor, 18);
    return `--tb-widget-button-main-color-pressed-filled: ${mainColorPressedFilled};\n`+
           `--tb-widget-button-main-color-pressed-ripple: ${mainColorPressedRipple};\n`+
           `--tb-widget-button-main-color-pressed-ripple-filled: ${mainColorPressedRippleFilled};`;
  }
}

class ActivatedButtonStateCssGenerator extends ButtonStateCssGenerator {

  protected get state(): WidgetButtonState {
    return WidgetButtonState.activated;
  }

  protected generateAdditionalStateCss(mainColor: string): string {
    const mainColorActivatedFilled = darkenColor(mainColor, 12);
    return `--tb-widget-button-main-color-activated-filled: ${mainColorActivatedFilled};`;
  }
}

class DisabledButtonStateCssGenerator extends ButtonStateCssGenerator {

  protected get state(): WidgetButtonState {
    return WidgetButtonState.disabled;
  }

  protected getMainColor(): string {
    return defaultMainColorDisabled;
  }

  protected getBackgroundColor(): string {
    return defaultBackgroundColorDisabled;
  }
}

const buttonStateCssGeneratorsMap = new Map<WidgetButtonState, ButtonStateCssGenerator>(
  [
    [WidgetButtonState.enabled, new EnabledButtonStateCssGenerator()],
    [WidgetButtonState.hovered, new HoveredButtonStateCssGenerator()],
    [WidgetButtonState.pressed, new PressedButtonStateCssGenerator()],
    [WidgetButtonState.activated, new ActivatedButtonStateCssGenerator()],
    [WidgetButtonState.disabled, new DisabledButtonStateCssGenerator()]
  ]
);

const widgetButtonCssSelector = '.mat-mdc-button.mat-mdc-button-base.tb-widget-button';

export const generateWidgetButtonAppearanceCss = (appearance: WidgetButtonAppearance): string => {
  let statesCss = '';
  for (const state of widgetButtonStates) {
    const generator = buttonStateCssGeneratorsMap.get(state);
    statesCss += `\n${generator.generateStateCss(appearance)}`;
  }
  return `${widgetButtonCssSelector} {\n`+
            `${statesCss}\n`+
    `}`;
};

const darkenColor = (inputColor: string, amount: number): string => {
  const input = tinycolor(inputColor);
  return input.darken(amount).toRgbString();
};
