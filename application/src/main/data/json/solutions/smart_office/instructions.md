## Solution instructions

As part of this solution, we have created the <a href="${MAIN_DASHBOARD_URL}" target="_blank">"Smart office"</a> dashboard that displays
data from multiple devices. You may use the dashboard to:

* observe office sensors and their location;
* browse indoor temperature and power consumption history;
* monitor temperature alarms;
* control HVAC (requires connected device);
* observe specific details for each sensor.

The dashboard has multiple states. The main state displays the list of the devices, their location on the office map as well as the list of their alarms.
You may drill down to the device details state by clicking on the table row. The device details are specific to the device type.

You may always customize the  <a href="${MAIN_DASHBOARD_URL}" target="_blank">"Smart office"</a> dashboard using dashboard development <a href="${DOCS_BASE_URL}/user-guide/dashboards/" target="_blank">guide</a>.

### Devices

We have already created "Office" asset and 4 devices related to it. We have also loaded demo data for those devices. See device info and credentials below:

${device_list_and_credentials}

Solution expects specific telemetry from each device based on its type. 
You may find payload examples and commands to send the data on behalf of the devices below.
The examples below use <a href="${DOCS_BASE_URL}/reference/http-api/#telemetry-upload-api" target="_blank">HTTP API</a>.
See <a href="${DOCS_BASE_URL}/getting-started-guides/connectivity/" target="_blank">connecting devices</a> for other connectivity options.


**Energy meter**


Payload example:

```json
{"voltage":  220, "frequency":  60, "amperage": 16, "power": 3000, "energy": 300 }{:copy-code}
```

To emulate the data upload on behalf of device "Energy meter", one should execute the following command:

```bash
curl -v -X POST -d "{\"voltage\":  220, \"frequency\":  60, \"amperage\": 16, \"power\": 3000, \"energy\": 300}" ${BASE_URL}/api/v1/${Energy meterACCESS_TOKEN}/telemetry --header "Content-Type:application/json"{:copy-code}
```

**Water meter**


Payload example:

```json
{"water": 2.3, "voltage": 3.9 }{:copy-code}
```

To emulate the data upload on behalf of device "Water meter", one should execute the following command:

```bash
curl -v -X POST -d "{\"water\": 2.3, \"voltage\": 3.9 }" ${BASE_URL}/api/v1/${Water meterACCESS_TOKEN}/telemetry --header "Content-Type:application/json"{:copy-code}
```

**Smart sensor**


Payload example:

```json
{"co2": 500, "tvoc": 0.3, "temperature": 22.5, "humidity": 50, "occupancy": true}{:copy-code}
```

To emulate the data upload on behalf of device "Smart sensor", one should execute the following command:

```bash
curl -v -X POST -d "{\"co2\": 500, \"tvoc\": 0.3, \"temperature\": 22.5, \"humidity\": 50, \"occupancy\": true}" ${BASE_URL}/api/v1/${Smart sensorACCESS_TOKEN}/telemetry --header "Content-Type:application/json"{:copy-code}
```

**HVAC**


Payload example:

```json
{"airFlow": 300, "targetTemperature": 21.5, "enabled": true}{:copy-code}
```

To emulate the data upload on behalf of device "HVAC", one should execute the following command:

```bash
curl -v -X POST -d "{\"airFlow\": 300, \"targetTemperature\": 21.5, \"enabled\": true}" ${BASE_URL}/api/v1/${HVACACCESS_TOKEN}/telemetry --header "Content-Type:application/json"{:copy-code}
``` 

HVAC device also accepts commands from the dashboard to enable/disable air conditioning as well as set target temperature.
The commands are sent using the platform <a href="${DOCS_BASE_URL}/user-guide/rpc/" target="_blank">RPC API</a>.

### Alarms

Alarms are generated using <a href="${DOCS_BASE_URL}/user-guide/device-profiles/#alarm-rules" target="_blank">Alarm rules</a> in the
"smart-sensor" <a href="/profiles/deviceProfiles" target="_blank">device profile</a>.

### Solution entities

As part of this solution, the following entities were created:

${all_entities}

### Edge computing

**Optionally**, this solution can be extended to use edge computing.

<a href="https://thingsboard.io/products/thingsboard-edge/" target="_blank">ThingsBoard Edge</a> allows bringing data analysis and management to the edge, where the data created.
At the same time ThingsBoard Edge seamlessly synchronizing with the ThingsBoard cloud according to your business needs.

As example, in the context of Smart Office solution, edge computing could be useful if you have remote offices that are scattered throughout the country. 
In this case, ThingsBoard Edge can be deployed into every office to process data from sensors, cameras, and other devices, enabling real-time analysis and decision-making, such as turning off lights or adjusting temperature automatically. 
Edge is going to process data in case there is no network connection to the central ThingsBoard server, and thus no data will be lost and required decisions are going to be taken locally. 
Eventually, required data is going to be pushed to the cloud, once network connection is established. 
Configuration of edge computing business logic is centralized in a single place - ThingsBoard server.

In the scope of this solution, new edge entity <a href="${Remote Office R1EDGE_DETAILS_URL}" target="_blank">Remote Office R1</a> was created.

Additionally, particular entity groups were already assigned to the edge entity to simplify the edge deployment:

* **"Buildings"** *ASSET* group;
* **"Office sensors"** *DEVICE* group;
* **"Smart office dashboards"** *DASHBOARD* group.

To install ThingsBoard Edge and connect to the cloud, please navigate to <a href="${Remote Office R1EDGE_DETAILS_URL}" target="_blank">edge details page</a> and click **Install & Connect instructions** button.

Once the edge is installed and connected to the cloud, you will be able to log in into edge using your tenant credentials.

#### Push data to device on edge

**"Office sensors"** *DEVICE* group was assigned to the edge entity "Remote Office R1".
This means that all devices from this group will be automatically provisioned to the edge.

You can see devices from this group once you log in into edge and navigate to the **Entities -> Devices** page.

To emulate the data upload on behalf of device "Energy meter" to the edge, one should execute the following command:

```bash
curl -v -X POST -d "{\"voltage\":  220, \"frequency\":  60, \"amperage\": 16, \"power\": 3000, \"energy\": 300}" http://localhost:8080/api/v1/${Energy meterACCESS_TOKEN}/telemetry --header "Content-Type:application/json"{:copy-code}
```

Or please use next command if you updated edge HTTP 8080 bind port to **18080** during edge installation:

```bash
curl -v -X POST -d "{\"voltage\":  220, \"frequency\":  60, \"amperage\": 16, \"power\": 3000, \"energy\": 300}" http://localhost:18080/api/v1/${Energy meterACCESS_TOKEN}/telemetry --header "Content-Type:application/json"{:copy-code}
```

Once you'll push data to the device "Energy meter" on edge, you'll be able to see telemetry update on the cloud for this device as well.
