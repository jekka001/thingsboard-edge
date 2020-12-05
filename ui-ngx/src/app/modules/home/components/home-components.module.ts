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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { AddEntityDialogComponent } from '@home/components/entity/add-entity-dialog.component';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { DetailsPanelComponent } from '@home/components/details-panel.component';
import { EntityDetailsPanelComponent } from '@home/components/entity/entity-details-panel.component';
import { AuditLogDetailsDialogComponent } from '@home/components/audit-log/audit-log-details-dialog.component';
import { AuditLogTableComponent } from '@home/components/audit-log/audit-log-table.component';
import { EventTableHeaderComponent } from '@home/components/event/event-table-header.component';
import { EventTableComponent } from '@home/components/event/event-table.component';
import { RelationTableComponent } from '@home/components/relation/relation-table.component';
import { RelationDialogComponent } from '@home/components/relation/relation-dialog.component';
import { AlarmTableHeaderComponent } from '@home/components/alarm/alarm-table-header.component';
import { AlarmTableComponent } from '@home/components/alarm/alarm-table.component';
import { AttributeTableComponent } from '@home/components/attribute/attribute-table.component';
import { AddAttributeDialogComponent } from '@home/components/attribute/add-attribute-dialog.component';
import { EditAttributeValuePanelComponent } from '@home/components/attribute/edit-attribute-value-panel.component';
import { DashboardComponent } from '@home/components/dashboard/dashboard.component';
import { WidgetComponent } from '@home/components/widget/widget.component';
import { WidgetComponentService } from '@home/components/widget/widget-component.service';
import { LegendComponent } from '@home/components/widget/legend.component';
import { AliasesEntitySelectPanelComponent } from '@home/components/alias/aliases-entity-select-panel.component';
import { AliasesEntitySelectComponent } from '@home/components/alias/aliases-entity-select.component';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import { EntityAliasesDialogComponent } from '@home/components/alias/entity-aliases-dialog.component';
import { EntityFilterViewComponent } from '@home/components/entity/entity-filter-view.component';
import { EntityAliasDialogComponent } from '@home/components/alias/entity-alias-dialog.component';
import { EntityFilterComponent } from '@home/components/entity/entity-filter.component';
import { RelationFiltersComponent } from '@home/components/relation/relation-filters.component';
import { EntityAliasSelectComponent } from '@home/components/alias/entity-alias-select.component';
import { DataKeysComponent } from '@home/components/widget/data-keys.component';
import { DataKeyConfigDialogComponent } from '@home/components/widget/data-key-config-dialog.component';
import { DataKeyConfigComponent } from '@home/components/widget/data-key-config.component';
import { LegendConfigPanelComponent } from '@home/components/widget/legend-config-panel.component';
import { LegendConfigComponent } from '@home/components/widget/legend-config.component';
import { ManageWidgetActionsComponent } from '@home/components/widget/action/manage-widget-actions.component';
import { WidgetActionDialogComponent } from '@home/components/widget/action/widget-action-dialog.component';
import { CustomActionPrettyResourcesTabsComponent } from '@home/components/widget/action/custom-action-pretty-resources-tabs.component';
import { CustomActionPrettyEditorComponent } from '@home/components/widget/action/custom-action-pretty-editor.component';
import { CustomDialogService } from '@home/components/widget/dialog/custom-dialog.service';
import { CustomDialogContainerComponent } from '@home/components/widget/dialog/custom-dialog-container.component';
import { ImportExportService } from '@home/components/import-export/import-export.service';
import { ImportDialogComponent } from '@home/components/import-export/import-dialog.component';
import { AddWidgetToDashboardDialogComponent } from '@home/components/attribute/add-widget-to-dashboard-dialog.component';
import { ImportDialogCsvComponent } from '@home/components/import-export/import-dialog-csv.component';
import { TableColumnsAssignmentComponent } from '@home/components/import-export/table-columns-assignment.component';
import { EventContentDialogComponent } from '@home/components/event/event-content-dialog.component';
import { SharedHomeComponentsModule } from '@home/components/shared-home-components.module';
import { SelectTargetLayoutDialogComponent } from '@home/components/dashboard/select-target-layout-dialog.component';
import { SelectTargetStateDialogComponent } from '@home/components/dashboard/select-target-state-dialog.component';
import { ConverterAutocompleteComponent } from '@home/components/converter/converter-autocomplete.component';
import { OperationTypeListComponent } from '@home/components/role/operation-type-list.component';
import { ResourceTypeAutocompleteComponent } from '@home/components/role/resource-type-autocomplete.component';
import { PermissionListComponent } from '@home/components/role/permission-list.component';
import { ViewRoleDialogComponent } from '@home/components/role/view-role-dialog.component';
import { GroupEntitiesTableComponent } from '@home/components/group/group-entities-table.component';
import { GroupEntityTabsComponent } from '@home/components/group/group-entity-tabs.component';
import { AddGroupEntityDialogComponent } from '@home/components/group/add-group-entity-dialog.component';
import { GroupEntityTableHeaderComponent } from '@home/components/group/group-entity-table-header.component';
import { GroupConfigTableConfigService } from '@home/components/group/group-config-table-config.service';
import { RegistrationPermissionsComponent } from './role/registration-permissions.component';
import { HomeDialogsModule } from '@home/dialogs/home-dialogs.module';
import { EntityGroupComponent } from '@home/components/group/entity-group.component';
import { EntityGroupTabsComponent } from '@home/components/group/entity-group-tabs.component';
import { EntityGroupSettingsComponent } from '@home/components/group/entity-group-settings.component';
import { EntityGroupColumnsComponent } from '@home/components/group/entity-group-columns.component';
import { EntityGroupColumnDialogComponent } from '@home/components/group/entity-group-column-dialog.component';
import { EntityGroupColumnComponent } from '@home/components/group/entity-group-column.component';
import { EntityGroupsTableConfigResolver } from '@home/components/group/entity-groups-table-config.resolver';
import { EntityGroupConfigResolver } from '@home/components/group/entity-group-config.resolver';
import { AliasesEntityAutocompleteComponent } from '@home/components/alias/aliases-entity-autocomplete.component';
import { BooleanFilterPredicateComponent } from '@home/components/filter/boolean-filter-predicate.component';
import { StringFilterPredicateComponent } from '@home/components/filter/string-filter-predicate.component';
import { NumericFilterPredicateComponent } from '@home/components/filter/numeric-filter-predicate.component';
import { ComplexFilterPredicateComponent } from '@home/components/filter/complex-filter-predicate.component';
import { FilterPredicateComponent } from '@home/components/filter/filter-predicate.component';
import { FilterPredicateListComponent } from '@home/components/filter/filter-predicate-list.component';
import { KeyFilterListComponent } from '@home/components/filter/key-filter-list.component';
import { ComplexFilterPredicateDialogComponent } from '@home/components/filter/complex-filter-predicate-dialog.component';
import { KeyFilterDialogComponent } from '@home/components/filter/key-filter-dialog.component';
import { FiltersDialogComponent } from '@home/components/filter/filters-dialog.component';
import { FilterDialogComponent } from '@home/components/filter/filter-dialog.component';
import { FilterSelectComponent } from '@home/components/filter/filter-select.component';
import { FiltersEditComponent } from '@home/components/filter/filters-edit.component';
import { FiltersEditPanelComponent } from '@home/components/filter/filters-edit-panel.component';
import { UserFilterDialogComponent } from '@home/components/filter/user-filter-dialog.component';
import { FilterUserInfoComponent } from '@home/components/filter/filter-user-info.component';
import { FilterUserInfoDialogComponent } from '@home/components/filter/filter-user-info-dialog.component';
import { FilterPredicateValueComponent } from '@home/components/filter/filter-predicate-value.component';
import { TenantProfileAutocompleteComponent } from '@home/components/profile/tenant-profile-autocomplete.component';
import { TenantProfileComponent } from '@home/components/profile/tenant-profile.component';
import { TenantProfileDialogComponent } from '@home/components/profile/tenant-profile-dialog.component';
import { TenantProfileDataComponent } from '@home/components/profile/tenant-profile-data.component';
import { DefaultDeviceProfileConfigurationComponent } from '@home/components/profile/device/default-device-profile-configuration.component';
import { DeviceProfileConfigurationComponent } from '@home/components/profile/device/device-profile-configuration.component';
import { DeviceProfileComponent } from '@home/components/profile/device-profile.component';
import { DefaultDeviceProfileTransportConfigurationComponent } from '@home/components/profile/device/default-device-profile-transport-configuration.component';
import { DeviceProfileTransportConfigurationComponent } from '@home/components/profile/device/device-profile-transport-configuration.component';
import { DeviceProfileDialogComponent } from '@home/components/profile/device-profile-dialog.component';
import { DeviceProfileAutocompleteComponent } from '@home/components/profile/device-profile-autocomplete.component';
import { MqttDeviceProfileTransportConfigurationComponent } from '@home/components/profile/device/mqtt-device-profile-transport-configuration.component';
import { Lwm2mDeviceProfileTransportConfigurationComponent } from '@home/components/profile/device/lwm2m-device-profile-transport-configuration.component';
import { DeviceProfileAlarmsComponent } from '@home/components/profile/alarm/device-profile-alarms.component';
import { DeviceProfileAlarmComponent } from '@home/components/profile/alarm/device-profile-alarm.component';
import { CreateAlarmRulesComponent } from '@home/components/profile/alarm/create-alarm-rules.component';
import { AlarmRuleComponent } from '@home/components/profile/alarm/alarm-rule.component';
import { AlarmRuleConditionComponent } from '@home/components/profile/alarm/alarm-rule-condition.component';
import { FilterTextComponent } from '@home/components/filter/filter-text.component';
import { AddDeviceProfileDialogComponent } from '@home/components/profile/add-device-profile-dialog.component';
import { RuleChainAutocompleteComponent } from '@home/components/rule-chain/rule-chain-autocomplete.component';
import { DeviceProfileProvisionConfigurationComponent } from '@home/components/profile/device-profile-provision-configuration.component';
import { AlarmScheduleComponent } from '@home/components/profile/alarm/alarm-schedule.component';
import { DeviceWizardDialogComponent } from '@home/components/wizard/device-wizard-dialog.component';
import { DeviceCredentialsComponent } from '@home/components/device/device-credentials.component';
import { AlarmScheduleInfoComponent } from '@home/components/profile/alarm/alarm-schedule-info.component';
import { AlarmScheduleDialogComponent } from '@home/components/profile/alarm/alarm-schedule-dialog.component';
import { EditAlarmDetailsDialogComponent } from '@home/components/profile/alarm/edit-alarm-details-dialog.component';
import { AlarmRuleConditionDialogComponent } from '@home/components/profile/alarm/alarm-rule-condition-dialog.component';
import { EntityGroupWizardDialogComponent } from '@home/components/wizard/entity-group-wizard-dialog.component';
import { DefaultTenantProfileConfigurationComponent } from '@home/components/profile/tenant/default-tenant-profile-configuration.component';
import { TenantProfileConfigurationComponent } from '@home/components/profile/tenant/tenant-profile-configuration.component';
import { SmsProviderConfigurationComponent } from '@home/components/sms/sms-provider-configuration.component';
import { AwsSnsProviderConfigurationComponent } from '@home/components/sms/aws-sns-provider-configuration.component';
import { TwilioSmsProviderConfigurationComponent } from '@home/components/sms/twilio-sms-provider-configuration.component';
import { CopyDeviceCredentialsComponent } from '@home/components/device/copy-device-credentials.component';

@NgModule({
  declarations:
    [
      EntitiesTableComponent,
      AddEntityDialogComponent,
      DetailsPanelComponent,
      EntityDetailsPanelComponent,
      AuditLogTableComponent,
      AuditLogDetailsDialogComponent,
      EventContentDialogComponent,
      EventTableHeaderComponent,
      EventTableComponent,
      RelationTableComponent,
      RelationDialogComponent,
      RelationFiltersComponent,
      AlarmTableHeaderComponent,
      AlarmTableComponent,
      AttributeTableComponent,
      AddAttributeDialogComponent,
      EditAttributeValuePanelComponent,
      AliasesEntitySelectPanelComponent,
      AliasesEntitySelectComponent,
      AliasesEntityAutocompleteComponent,
      EntityAliasesDialogComponent,
      EntityAliasDialogComponent,
      DashboardComponent,
      WidgetComponent,
      LegendComponent,
      WidgetConfigComponent,
      EntityFilterViewComponent,
      EntityFilterComponent,
      EntityAliasSelectComponent,
      DataKeysComponent,
      DataKeyConfigComponent,
      DataKeyConfigDialogComponent,
      LegendConfigPanelComponent,
      LegendConfigComponent,
      ManageWidgetActionsComponent,
      WidgetActionDialogComponent,
      CustomActionPrettyResourcesTabsComponent,
      CustomActionPrettyEditorComponent,
      CustomDialogContainerComponent,
      ImportDialogComponent,
      ImportDialogCsvComponent,
      SelectTargetLayoutDialogComponent,
      SelectTargetStateDialogComponent,
      AddWidgetToDashboardDialogComponent,
      TableColumnsAssignmentComponent,
      ConverterAutocompleteComponent,
      OperationTypeListComponent,
      ResourceTypeAutocompleteComponent,
      PermissionListComponent,
      ViewRoleDialogComponent,
      GroupEntitiesTableComponent,
      GroupEntityTabsComponent,
      GroupEntityTableHeaderComponent,
      EntityGroupComponent,
      EntityGroupTabsComponent,
      EntityGroupSettingsComponent,
      EntityGroupColumnComponent,
      EntityGroupColumnsComponent,
      EntityGroupColumnDialogComponent,
      AddGroupEntityDialogComponent,
      RegistrationPermissionsComponent,
      BooleanFilterPredicateComponent,
      StringFilterPredicateComponent,
      NumericFilterPredicateComponent,
      ComplexFilterPredicateComponent,
      ComplexFilterPredicateDialogComponent,
      FilterPredicateComponent,
      FilterPredicateListComponent,
      KeyFilterListComponent,
      KeyFilterDialogComponent,
      FilterDialogComponent,
      FiltersDialogComponent,
      FilterSelectComponent,
      FilterTextComponent,
      FiltersEditComponent,
      FiltersEditPanelComponent,
      UserFilterDialogComponent,
      FilterUserInfoComponent,
      FilterUserInfoDialogComponent,
      FilterPredicateValueComponent,
      TenantProfileAutocompleteComponent,
      DefaultTenantProfileConfigurationComponent,
      TenantProfileConfigurationComponent,
      TenantProfileDataComponent,
      TenantProfileComponent,
      TenantProfileDialogComponent,
      DeviceProfileAutocompleteComponent,
      DefaultDeviceProfileConfigurationComponent,
      DeviceProfileConfigurationComponent,
      DefaultDeviceProfileTransportConfigurationComponent,
      MqttDeviceProfileTransportConfigurationComponent,
      Lwm2mDeviceProfileTransportConfigurationComponent,
      DeviceProfileTransportConfigurationComponent,
      CreateAlarmRulesComponent,
      AlarmRuleComponent,
      AlarmRuleConditionDialogComponent,
      AlarmRuleConditionComponent,
      DeviceProfileAlarmComponent,
      DeviceProfileAlarmsComponent,
      DeviceProfileComponent,
      DeviceProfileDialogComponent,
      AddDeviceProfileDialogComponent,
      RuleChainAutocompleteComponent,
      AlarmScheduleInfoComponent,
      DeviceProfileProvisionConfigurationComponent,
      AlarmScheduleComponent,
      DeviceWizardDialogComponent,
      DeviceCredentialsComponent,
      CopyDeviceCredentialsComponent,
      AlarmScheduleDialogComponent,
      EditAlarmDetailsDialogComponent,
      SmsProviderConfigurationComponent,
      AwsSnsProviderConfigurationComponent,
      TwilioSmsProviderConfigurationComponent,
      EntityGroupWizardDialogComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    SharedHomeComponentsModule,
    HomeDialogsModule
  ],
  exports: [
    SharedHomeComponentsModule,
    EntitiesTableComponent,
    AddEntityDialogComponent,
    DetailsPanelComponent,
    EntityDetailsPanelComponent,
    AuditLogTableComponent,
    EventTableComponent,
    RelationTableComponent,
    RelationFiltersComponent,
    AlarmTableComponent,
    AttributeTableComponent,
    AliasesEntitySelectComponent,
    AliasesEntityAutocompleteComponent,
    EntityAliasesDialogComponent,
    EntityAliasDialogComponent,
    DashboardComponent,
    WidgetComponent,
    LegendComponent,
    WidgetConfigComponent,
    EntityFilterViewComponent,
    EntityFilterComponent,
    EntityAliasSelectComponent,
    DataKeysComponent,
    DataKeyConfigComponent,
    DataKeyConfigDialogComponent,
    LegendConfigComponent,
    ManageWidgetActionsComponent,
    WidgetActionDialogComponent,
    CustomActionPrettyResourcesTabsComponent,
    CustomActionPrettyEditorComponent,
    CustomDialogContainerComponent,
    ImportDialogComponent,
    ImportDialogCsvComponent,
    TableColumnsAssignmentComponent,
    SelectTargetLayoutDialogComponent,
    SelectTargetStateDialogComponent,
    ConverterAutocompleteComponent,
    OperationTypeListComponent,
    ResourceTypeAutocompleteComponent,
    PermissionListComponent,
    ViewRoleDialogComponent,
    GroupEntitiesTableComponent,
    GroupEntityTabsComponent,
    GroupEntityTableHeaderComponent,
    EntityGroupComponent,
    EntityGroupTabsComponent,
    EntityGroupSettingsComponent,
    EntityGroupColumnComponent,
    EntityGroupColumnsComponent,
    EntityGroupColumnDialogComponent,
    AddGroupEntityDialogComponent,
    RegistrationPermissionsComponent,
    BooleanFilterPredicateComponent,
    StringFilterPredicateComponent,
    NumericFilterPredicateComponent,
    ComplexFilterPredicateComponent,
    ComplexFilterPredicateDialogComponent,
    FilterPredicateComponent,
    FilterPredicateListComponent,
    KeyFilterListComponent,
    KeyFilterDialogComponent,
    FilterDialogComponent,
    FiltersDialogComponent,
    FilterSelectComponent,
    FilterTextComponent,
    FiltersEditComponent,
    UserFilterDialogComponent,
    TenantProfileAutocompleteComponent,
    TenantProfileDataComponent,
    TenantProfileComponent,
    TenantProfileDialogComponent,
    DeviceProfileAutocompleteComponent,
    DefaultDeviceProfileConfigurationComponent,
    DeviceProfileConfigurationComponent,
    DefaultDeviceProfileTransportConfigurationComponent,
    MqttDeviceProfileTransportConfigurationComponent,
    Lwm2mDeviceProfileTransportConfigurationComponent,
    DeviceProfileTransportConfigurationComponent,
    CreateAlarmRulesComponent,
    AlarmRuleComponent,
    AlarmRuleConditionDialogComponent,
    AlarmRuleConditionComponent,
    DeviceProfileAlarmComponent,
    DeviceProfileAlarmsComponent,
    DeviceProfileComponent,
    DeviceProfileDialogComponent,
    AddDeviceProfileDialogComponent,
    RuleChainAutocompleteComponent,
    DeviceWizardDialogComponent,
    DeviceCredentialsComponent,
    CopyDeviceCredentialsComponent,
    AlarmScheduleInfoComponent,
    AlarmScheduleComponent,
    AlarmScheduleDialogComponent,
    EditAlarmDetailsDialogComponent,
    DeviceProfileProvisionConfigurationComponent,
    SmsProviderConfigurationComponent,
    AwsSnsProviderConfigurationComponent,
    TwilioSmsProviderConfigurationComponent,
    EntityGroupWizardDialogComponent
  ],
  providers: [
    WidgetComponentService,
    CustomDialogService,
    ImportExportService,
    GroupConfigTableConfigService,
    EntityGroupsTableConfigResolver,
    EntityGroupConfigResolver
  ]
})
export class HomeComponentsModule { }
