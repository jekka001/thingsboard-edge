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

import { Component } from '@angular/core';
import { deepTrim, isDefinedAndNotNull } from '@core/public-api';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { RuleNodeConfiguration, RuleNodeConfigurationComponent } from '@app/shared/models/rule-node.models';
import {
  allowedOriginatorFields,
  DataToFetch,
  dataToFetchTranslations,
  FetchTo,
  msgMetadataLabelTranslations,
  SvMapOption
} from '../rule-node-config.models';
import { entityFields } from '@shared/models/entity.models';

@Component({
  selector: 'tb-enrichment-node-related-attributes-config',
  templateUrl: './related-attributes-config.component.html',
  styleUrls: []
})
export class RelatedAttributesConfigComponent extends RuleNodeConfigurationComponent {

  relatedAttributesConfigForm: FormGroup;

  protected readonly DataToFetch = DataToFetch;

  public msgMetadataLabelTranslations = msgMetadataLabelTranslations;
  public originatorFields: SvMapOption[] = [];
  public fetchToData = [];

  constructor(private fb: FormBuilder,
              private translate: TranslateService) {
    super();
    for (const field of Object.keys(allowedOriginatorFields)) {
      this.originatorFields.push({
        value: allowedOriginatorFields[field].value,
        name: this.translate.instant(allowedOriginatorFields[field].name)
      });
    }
    for (const key of dataToFetchTranslations.keys()) {
      this.fetchToData.push({
        value: key,
        name: this.translate.instant(dataToFetchTranslations.get(key as DataToFetch))
      });
    }
  }

  protected configForm(): FormGroup {
    return this.relatedAttributesConfigForm;
  }

  protected prepareOutputConfig(configuration: RuleNodeConfiguration): RuleNodeConfiguration {
    if (configuration.dataToFetch === DataToFetch.FIELDS) {
      configuration.dataMapping = configuration.svMap;
      delete configuration.svMap;
    } else {
      configuration.dataMapping = configuration.kvMap;
      delete configuration.kvMap;
    }

    const filteDataMapping = {};
    if (configuration && configuration.dataMapping) {
      for (const key of Object.keys(configuration.dataMapping)) {
        filteDataMapping[key.trim()] = configuration.dataMapping[key];
      }
    }
    configuration.dataMapping = filteDataMapping;
    delete configuration.svMap;
    delete configuration.kvMap;

    return deepTrim(configuration);
  }

  protected prepareInputConfig(configuration: RuleNodeConfiguration): RuleNodeConfiguration {
    let svMap = {
      [entityFields.name.value]: `relatedEntity${this.translate.instant(entityFields.name.name)}`
    };
    let kvMap = {
      serialNumber: 'sn'
    };

    let dataToFetch: DataToFetch;
    if (isDefinedAndNotNull(configuration?.telemetry)) {
      dataToFetch = configuration.telemetry ? DataToFetch.LATEST_TELEMETRY : DataToFetch.ATTRIBUTES;
    } else {
      dataToFetch = isDefinedAndNotNull(configuration?.dataToFetch) ? configuration.dataToFetch : DataToFetch.ATTRIBUTES;
    }

    let dataMapping;
    if (isDefinedAndNotNull(configuration?.attrMapping)) {
      dataMapping = configuration.attrMapping;
    } else {
      dataMapping = isDefinedAndNotNull(configuration?.dataMapping) ? configuration.dataMapping : null;
    }

    if (dataToFetch === DataToFetch.FIELDS) {
      svMap = dataMapping;
    } else {
      kvMap = dataMapping;
    }

    return {
      relationsQuery: isDefinedAndNotNull(configuration?.relationsQuery) ? configuration.relationsQuery : null,
      dataToFetch,
      svMap,
      kvMap,
      fetchTo: isDefinedAndNotNull(configuration?.fetchTo) ? configuration.fetchTo : FetchTo.METADATA
    };
  }

  public selectTranslation(latestTelemetryTranslation: string, attributesTranslation: string) {
    if (this.relatedAttributesConfigForm.get('dataToFetch').value === DataToFetch.LATEST_TELEMETRY) {
      return latestTelemetryTranslation;
    } else {
      return attributesTranslation;
    }
  }

  protected onConfigurationSet(configuration: RuleNodeConfiguration) {
    this.relatedAttributesConfigForm = this.fb.group({
      relationsQuery: [configuration.relationsQuery, [Validators.required]],
      dataToFetch: [configuration.dataToFetch, []],
      kvMap: [configuration.kvMap, [Validators.required]],
      svMap: [configuration.svMap, [Validators.required]],
      fetchTo: [configuration.fetchTo, []]
    });
  }

  protected validatorTriggers(): string[] {
    return ['dataToFetch'];
  }

  protected updateValidators(emitEvent: boolean) {
    if (this.relatedAttributesConfigForm.get('dataToFetch').value === DataToFetch.FIELDS) {
      this.relatedAttributesConfigForm.get('svMap').enable({emitEvent: false});
      this.relatedAttributesConfigForm.get('kvMap').disable({emitEvent: false});
      this.relatedAttributesConfigForm.get('svMap').updateValueAndValidity();
    } else {
      this.relatedAttributesConfigForm.get('svMap').disable({emitEvent: false});
      this.relatedAttributesConfigForm.get('kvMap').enable({emitEvent: false});
      this.relatedAttributesConfigForm.get('kvMap').updateValueAndValidity();
    }
  }
}
