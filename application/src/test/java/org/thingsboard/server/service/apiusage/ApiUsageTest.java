package org.thingsboard.server.service.apiusage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.ApiUsageStateValue;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.SaveDeviceWithCredentialsRequest;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.data.tenant.profile.TenantProfileData;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.controller.TbUrlConstants;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.dao.usagerecord.ApiUsageStateService;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class ApiUsageTest extends AbstractControllerTest {

    private Tenant savedTenant;
    private User tenantAdmin;

    private static final int MAX_DP_ENABLE_VALUE = 12;
    private static final double WARN_THRESHOLD_VALUE = 0.5;
    @Autowired
    private ApiUsageStateService apiUsageStateService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        TenantProfile tenantProfile = createTenantProfile();
        TenantProfile savedTenantProfile = doPost("/api/tenantProfile", tenantProfile, TenantProfile.class);
        assertNotNull(savedTenantProfile);

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        tenant.setTenantProfileId(savedTenantProfile.getId());
        savedTenant = saveTenant(tenant);
        tenantId = savedTenant.getId();
        assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
    }

    @Test
    public void testTelemetryApiCall() throws Exception {
        Device device = createDevice();
        assertNotNull(device);
        String telemetryPayload = "{\"temperature\":25, \"humidity\":60}";
        String url = TbUrlConstants.TELEMETRY_URL_PREFIX + "/DEVICE/" + device.getId() + "/timeseries/ANY";

        long VALUE_WARNING = (long) (MAX_DP_ENABLE_VALUE * WARN_THRESHOLD_VALUE) / 2;

        for (int i = 0; i < VALUE_WARNING; i++) {
            doPostAsync(url, telemetryPayload, String.class, status().isOk());
        }

        await().atMost(TIMEOUT, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(ApiUsageStateValue.WARNING, apiUsageStateService.findTenantApiUsageState(tenantId).getDbStorageState()));

        long VALUE_DISABLE = (long) (MAX_DP_ENABLE_VALUE - (MAX_DP_ENABLE_VALUE * WARN_THRESHOLD_VALUE)) / 2;

        for (int i = 0; i < VALUE_DISABLE; i++) {
            doPostAsync(url, telemetryPayload, String.class, status().isOk());
        }

        await().atMost(TIMEOUT, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertEquals(ApiUsageStateValue.DISABLED, apiUsageStateService.findTenantApiUsageState(tenantId).getDbStorageState());
                });
    }

    private TenantProfile createTenantProfile() {
        TenantProfile tenantProfile = new TenantProfile();
        tenantProfile.setName("Tenant Profile");
        tenantProfile.setDescription("Tenant Profile" + " Test");

        TenantProfileData tenantProfileData = new TenantProfileData();
        DefaultTenantProfileConfiguration config = DefaultTenantProfileConfiguration.builder()
                .maxDPStorageDays(MAX_DP_ENABLE_VALUE)
                .warnThreshold(WARN_THRESHOLD_VALUE)
                .build();

        tenantProfileData.setConfiguration(config);
        tenantProfile.setProfileData(tenantProfileData);
        return tenantProfile;
    }

    private Device createDevice() throws Exception {
        String testToken = "TEST_TOKEN";

        Device device = new Device();
        device.setName("My device");
        device.setType("default");
        device.setTenantId(tenantId);

        DeviceCredentials deviceCredentials = new DeviceCredentials();
        deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
        deviceCredentials.setCredentialsId(testToken);

        SaveDeviceWithCredentialsRequest saveRequest = new SaveDeviceWithCredentialsRequest(device, deviceCredentials);

        return readResponse(doPost("/api/device-with-credentials", saveRequest).andExpect(status().isOk()), Device.class);
    }

}
