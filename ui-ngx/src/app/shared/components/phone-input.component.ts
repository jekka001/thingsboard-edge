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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { Country, CountryData } from '@shared/models/country.models';
import examples from 'libphonenumber-js/examples.mobile.json';
import { Subscription } from 'rxjs';
import { FloatLabelType, MatFormFieldAppearance } from '@angular/material/form-field';

@Component({
  selector: 'tb-phone-input',
  templateUrl: './phone-input.component.html',
  styleUrls: ['./phone-input.component.scss'],
  providers: [
    CountryData,
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PhoneInputComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => PhoneInputComponent),
      multi: true
    }
  ]
})
export class PhoneInputComponent implements OnInit, ControlValueAccessor, Validator {

  @Input()
  disabled: boolean;

  @Input()
  defaultCountry = 'US';

  @Input()
  enableFlagsSelect = true;

  @Input()
  required = true;

  @Input()
  floatLabel: FloatLabelType = 'auto';

  @Input()
  appearance: MatFormFieldAppearance = 'fill';

  @Input()
  placeholder;

  @Input()
  label = this.translate.instant('phone-input.phone-input-label');

  get showFlagSelect(): boolean {
    return this.enableFlagsSelect && !this.isLegacy;
  }

  allCountries: Array<Country> = this.countryCodeData.allCountries;
  phonePlaceholder = '+12015550123';
  flagIcon: string;
  phoneFormGroup: UntypedFormGroup;

  private isLoading = true;
  get isLoad(): boolean {
    return this.isLoading;
  }

  set isLoad(value) {
    if (this.isLoading) {
      this.isLoading = value;
      if (this.defaultCountry) {
        this.getFlagAndPhoneNumberData(this.defaultCountry);
      }
      if (this.phoneFormGroup && this.phoneFormGroup.get('phoneNumber').value) {
        const parsedPhoneNumber = this.parsePhoneNumberFromString(this.phoneFormGroup.get('phoneNumber').value);
        this.defineCountryFromNumber(parsedPhoneNumber);
      }
    }
  }

  private isLegacy = false;
  private getExampleNumber;
  private parsePhoneNumberFromString;
  private baseCode = 127397;
  private countryCallingCode = '+';
  private modelValue: string;
  private changeSubscriptions: Subscription[] = [];
  private validators: ValidatorFn[] = [this.validatePhoneNumber()];

  private propagateChange = (v: any) => { };

  constructor(private translate: TranslateService,
              private fb: UntypedFormBuilder,
              private countryCodeData: CountryData) {
    import('libphonenumber-js/max').then((libphonenubmer) => {
      this.parsePhoneNumberFromString = libphonenubmer.parsePhoneNumberFromString;
      this.getExampleNumber = libphonenubmer.getExampleNumber;
    }).then(() => this.isLoad = false);
  }

  ngOnInit(): void {
    if (this.required) {
      this.validators.push(Validators.required);
    }
    this.phoneFormGroup = this.fb.group({
      country: [null, []],
      phoneNumber: [null, this.validators]
    });

    this.changeSubscriptions.push(this.phoneFormGroup.get('phoneNumber').valueChanges.subscribe(value => {
      let parsedPhoneNumber = null;
      if (value && this.parsePhoneNumberFromString) {
        parsedPhoneNumber = this.parsePhoneNumberFromString(value);
        this.defineCountryFromNumber(parsedPhoneNumber);
      }
      this.updateModel(parsedPhoneNumber);
    }));

    this.changeSubscriptions.push(this.phoneFormGroup.get('country').valueChanges.subscribe(value => {
      if (value) {
        const code = this.countryCallingCode;
        this.getFlagAndPhoneNumberData(value);
        let phoneNumber = this.phoneFormGroup.get('phoneNumber').value;
        if (phoneNumber) {
          if (code !== '+' && code !== this.countryCallingCode && phoneNumber.includes(code)) {
            phoneNumber = phoneNumber.replace(code, this.countryCallingCode);
            this.phoneFormGroup.get('phoneNumber').patchValue(phoneNumber);
          }
        }
      }
    }));
  }

  ngOnDestroy() {
    for (const subscription of this.changeSubscriptions) {
      subscription.unsubscribe();
    }
  }

  focus() {
    const phoneNumber = this.phoneFormGroup.get('phoneNumber');
    if (!phoneNumber.value) {
      phoneNumber.patchValue(this.countryCallingCode, {emitEvent: true});
    }
  }

  private getFlagAndPhoneNumberData(country) {
    if (this.enableFlagsSelect) {
      this.flagIcon = this.getFlagIcon(country);
    }
    this.getPhoneNumberData(country);
  }

  private getPhoneNumberData(country): void {
    if (this.getExampleNumber) {
      const phoneData = this.getExampleNumber(country, examples);
      this.phonePlaceholder = phoneData.number;
      this.countryCallingCode = `+${this.enableFlagsSelect ? phoneData.countryCallingCode : ''}`;
    }
  }

  private getFlagIcon(countryCode) {
    return String.fromCodePoint(...countryCode.split('').map(country => this.baseCode + country.charCodeAt(0)));
  }

  private updateModelValueInFormat(parsedPhoneNumber: any) {
    this.modelValue = parsedPhoneNumber.format('E.164');
  }

  validatePhoneNumber(): ValidatorFn {
    return (c: UntypedFormControl) => {
      const phoneNumber = c.value;
      if (phoneNumber && this.parsePhoneNumberFromString) {
        const parsedPhoneNumber = this.parsePhoneNumberFromString(phoneNumber);
        if (!parsedPhoneNumber?.isValid() || !parsedPhoneNumber?.isPossible()) {
          return {
            invalidPhoneNumber: {
              valid: false
            }
          };
        }
      }
      return null;
    };
  }

  private defineCountryFromNumber(parsedPhoneNumber) {
    const country = this.phoneFormGroup.get('country').value;
    if (parsedPhoneNumber?.country && parsedPhoneNumber?.country !== country) {
      this.phoneFormGroup.get('country').patchValue(parsedPhoneNumber.country, {emitEvent: true});
    }
  }

  validate(): ValidationErrors | null {
    const phoneNumber = this.phoneFormGroup.get('phoneNumber');
    return phoneNumber.valid || this.countryCallingCode === phoneNumber.value ? null : {
      phoneFormGroup: false
    };
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.phoneFormGroup.disable({emitEvent: false});
    } else {
      this.phoneFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(phoneNumber): void {
    this.modelValue = phoneNumber;
    let country = this.defaultCountry;
    if (this.parsePhoneNumberFromString) {
      this.phoneFormGroup.get('phoneNumber').clearValidators();
      this.phoneFormGroup.get('phoneNumber').setValidators(this.validators);
      if (phoneNumber) {
        const parsedPhoneNumber = this.parsePhoneNumberFromString(phoneNumber);
        if (parsedPhoneNumber?.isValid() && parsedPhoneNumber?.isPossible()) {
          country = parsedPhoneNumber?.country || this.defaultCountry;
          this.updateModelValueInFormat(parsedPhoneNumber);
          this.isLegacy = false;
        } else {
          const validators = [Validators.maxLength(255)];
          if (this.required) {
            validators.push(Validators.required);
          }
          this.phoneFormGroup.get('phoneNumber').setValidators(validators);
          this.isLegacy = true;
        }
      } else {
        this.isLegacy = false;
      }
      this.phoneFormGroup.updateValueAndValidity({emitEvent: false});
      this.getFlagAndPhoneNumberData(country);
    }
    this.phoneFormGroup.reset({phoneNumber, country}, {emitEvent: false});
  }

  private updateModel(parsedPhoneNumber?) {
    const phoneNumber = this.phoneFormGroup.get('phoneNumber');
    if (phoneNumber.value === '+' || phoneNumber.value === this.countryCallingCode) {
      this.propagateChange(null);
    } else if (phoneNumber.valid) {
      this.modelValue = phoneNumber.value;
      if (parsedPhoneNumber) {
        this.updateModelValueInFormat(parsedPhoneNumber);
      }
      this.propagateChange(this.modelValue);
    } else {
      this.propagateChange(null);
    }
  }
}
