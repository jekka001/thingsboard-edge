/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.install;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.thingsboard.server.service.component.ComponentDiscoveryService;
import org.thingsboard.server.service.install.DatabaseEntitiesUpgradeService;
import org.thingsboard.server.service.install.EntityDatabaseSchemaService;
import org.thingsboard.server.service.install.InstallScripts;
import org.thingsboard.server.service.install.NoSqlKeyspaceService;
import org.thingsboard.server.service.install.SystemDataLoaderService;
import org.thingsboard.server.service.install.TsDatabaseSchemaService;
import org.thingsboard.server.service.install.TsLatestDatabaseSchemaService;
import org.thingsboard.server.service.install.migrate.TsLatestMigrateService;
import org.thingsboard.server.service.install.update.CacheCleanupService;
import org.thingsboard.server.service.install.update.DataUpdateService;

import static org.thingsboard.server.service.install.update.DefaultDataUpdateService.getEnv;

@Service
@Profile("install")
@Slf4j
public class ThingsboardInstallService {

    @Value("${install.upgrade:false}")
    private Boolean isUpgrade;

    @Value("${install.upgrade.from_version:1.3.0}")
    private String upgradeFromVersion;

    @Value("${install.load_demo:false}")
    private Boolean loadDemo;

    @Value("${state.persistToTelemetry:false}")
    private boolean persistToTelemetry;

    @Autowired
    private EntityDatabaseSchemaService entityDatabaseSchemaService;

    @Autowired(required = false)
    private NoSqlKeyspaceService noSqlKeyspaceService;

    @Autowired
    private TsDatabaseSchemaService tsDatabaseSchemaService;

    @Autowired(required = false)
    private TsLatestDatabaseSchemaService tsLatestDatabaseSchemaService;

    @Autowired
    private DatabaseEntitiesUpgradeService databaseEntitiesUpgradeService;

    @Autowired
    private ComponentDiscoveryService componentDiscoveryService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SystemDataLoaderService systemDataLoaderService;

    @Autowired
    private DataUpdateService dataUpdateService;

    @Autowired
    private CacheCleanupService cacheCleanupService;

    @Autowired(required = false)
    private TsLatestMigrateService latestMigrateService;

    @Autowired
    private InstallScripts installScripts;

    public void performInstall() {
        try {
            if (isUpgrade) {
                log.info("Starting ThingsBoard Edge Upgrade from version {} ...", upgradeFromVersion);

                cacheCleanupService.clearCache(upgradeFromVersion);

                if ("cassandra-latest-to-postgres".equals(upgradeFromVersion)) {
                    log.info("Migrating ThingsBoard latest timeseries data from cassandra to SQL database ...");
                    latestMigrateService.migrate();
                } else if (upgradeFromVersion.equals("3.6.2-images")) {
//                    installScripts.updateImages();
                } else {
                    switch (upgradeFromVersion) {
                        case "3.5.0":
                            log.info("Upgrading ThingsBoard Edge from version 3.5.0 to 3.5.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.5.0");
                        case "3.5.1":
                            log.info("Upgrading ThingsBoard Edge from version 3.5.1 to 3.6.0 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.5.1");
                            dataUpdateService.updateData("3.5.1");
                            systemDataLoaderService.updateDefaultNotificationConfigs();
                        case "3.6.0":
                            log.info("Upgrading ThingsBoard Edge from version 3.6.0 to 3.6.1 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.0");
                            dataUpdateService.updateData("3.6.0");
                        case "3.6.1":
                            log.info("Upgrading ThingsBoard Edge from version 3.6.1 to 3.6.2 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.1");
                            if (!getEnv("SKIP_IMAGES_MIGRATION", false)) {
                                installScripts.setUpdateImages(true);
                            } else {
                                log.info("Skipping images migration. Run the upgrade with fromVersion as '3.6.2-images' to migrate");
                            }
                        case "3.6.2":
                            log.info("Upgrading ThingsBoard from version 3.6.2 to 3.6.3 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.2");
                            systemDataLoaderService.updateDefaultNotificationConfigs();
                        case "3.6.3":
                            log.info("Upgrading ThingsBoard from version 3.6.3 to 3.6.4 ...");
                            databaseEntitiesUpgradeService.upgradeDatabase("3.6.3");
                            //TODO DON'T FORGET to update switch statement in the CacheCleanupService if you need to clear the cache
                        case "edge": // leave this after latest case version
                            // reset full sync required - to upload the latest widgets from cloud
                            // tenantsFullSyncRequiredUpdater and fixDuplicateSystemWidgetsBundles moved to 'edge' version
                            dataUpdateService.updateData("edge");
                            break;
                        case "CE":
                            log.info("Upgrading ThingsBoard Edge from version CE to PE ...");
                            //TODO: check CE schema version before launch of the update.
                            //TODO DON'T FORGET to update switch statement in the CacheCleanupService if you need to clear the cache
                            break;
                        default:
                            throw new RuntimeException("Unable to upgrade ThingsBoard Edge, unsupported fromVersion: " + upgradeFromVersion);
                    }
                    // We always run the CE to PE update script, just in case..
                    databaseEntitiesUpgradeService.upgradeDatabase("ce");
                    entityDatabaseSchemaService.createOrUpdateViewsAndFunctions();
                    entityDatabaseSchemaService.createOrUpdateDeviceInfoView(persistToTelemetry);
                    dataUpdateService.updateData("ce");

                    log.info("Updating system data...");
                    // dataUpdateService.upgradeRuleNodes();
                    systemDataLoaderService.loadSystemWidgets();
                    // installScripts.loadSystemLwm2mResources();
                    installScripts.loadSystemImages();
                    if (installScripts.isUpdateImages()) {
                        installScripts.updateImages();
                    }
                }

                log.info("Upgrade finished successfully!");

            } else {

                log.info("Starting ThingsBoard Edge Installation...");

                log.info("Installing DataBase schema for entities...");

                entityDatabaseSchemaService.createDatabaseSchema();

                entityDatabaseSchemaService.createOrUpdateViewsAndFunctions();
                entityDatabaseSchemaService.createOrUpdateDeviceInfoView(persistToTelemetry);

                log.info("Installing DataBase schema for timeseries...");

                if (noSqlKeyspaceService != null) {
                   noSqlKeyspaceService.createDatabaseSchema();
                }

                tsDatabaseSchemaService.createDatabaseSchema();

                if (tsLatestDatabaseSchemaService != null) {
                    tsLatestDatabaseSchemaService.createDatabaseSchema();
                }

                log.info("Loading system data...");

                componentDiscoveryService.discoverComponents();

                // systemDataLoaderService.createSysAdmin();
                systemDataLoaderService.createDefaultTenantProfiles();
                systemDataLoaderService.createAdminSettings();
                systemDataLoaderService.createRandomJwtSettings();
                systemDataLoaderService.loadSystemWidgets();
                // systemDataLoaderService.createOAuth2Templates();
                // systemDataLoaderService.createQueues();
                // systemDataLoaderService.createDefaultNotificationConfigs();

                // systemDataLoaderService.loadSystemPlugins();
                // systemDataLoaderService.loadSystemRules();
                // installScripts.loadSystemLwm2mResources();
                installScripts.loadSystemImages();

                if (loadDemo) {
                    // log.info("Loading demo data...");
                    // systemDataLoaderService.loadDemoData();
                }
                log.info("Installation finished successfully!");
            }


        } catch (Exception e) {
            log.error("Unexpected error during ThingsBoard Edge installation!", e);
            throw new ThingsboardInstallException("Unexpected error during ThingsBoard Edge installation!", e);
        } finally {
            SpringApplication.exit(context);
        }
    }

}

