#
# ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
#
# Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
#
# NOTICE: All information contained herein is, and remains
# the property of ThingsBoard, Inc. and its suppliers,
# if any.  The intellectual and technical concepts contained
# herein are proprietary to ThingsBoard, Inc.
# and its suppliers and may be covered by U.S. and Foreign Patents,
# patents in process, and are protected by trade secret or copyright law.
#
# Dissemination of this information or reproduction of this material is strictly forbidden
# unless prior written permission is obtained from COMPANY.
#
# Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
# managers or contractors who have executed Confidentiality and Non-disclosure agreements
# explicitly covering such access.
#
# The copyright notice above does not evidence any actual or intended publication
# or disclosure  of  this source code, which includes
# information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
# ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
# OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
# THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
# AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
# THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
# DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
# OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
#

import sys
import re


def extract_properties_with_comments(yaml_file_path):
    properties = {}

    with open(yaml_file_path, 'r') as file:
        lines = file.readlines()
        index = 0
        key_level_map = {0: ''}
        parse_line('', '', key_level_map, 0, index, lines, properties)

    return properties


def parse_line(table_name, comment, key_level_map, parent_line_level, index, lines, properties):
    if index >= len(lines):
        return
    line = lines[index]
    line_level = (len(line) - len(line.lstrip())) if line.strip() else 0
    line = line.strip()
    # if line is empty - parse next line
    if not line:
        index = index + 1
        parse_line(table_name, comment, key_level_map, line_level, index, lines, properties)
    # if line is a comment - save comment and parse next line
    else:
        if line_level == 0:
            key_level_map = {0: ''}
        if line.startswith('#'):
            if line_level == 0:
                table_name = line.lstrip('#')
            elif line_level == parent_line_level:
                comment = comment + '\n' + line.lstrip('#')
            else:
                comment = line.lstrip('#')
            index = index + 1
            parse_line(table_name, comment, key_level_map, line_level, index, lines, properties)
        else:
            # Check if it's a property line
            if ':' in line:
                # clean comment if level was changed
                if line_level != parent_line_level:
                    comment = ''
                key, value = line.split(':', 1)
                if key.startswith('- '):
                    key = key.lstrip('- ')
                key_level_map[line_level] = key
                value = value.strip()
                if value.split('#')[0]:
                    current_key = ''
                    for k in key_level_map.keys():
                        if k <= line_level:
                            current_key = ((current_key + '.') if current_key else '') + key_level_map[k]
                    properties[current_key] = (value, comment, table_name)
                    comment = ''
                index = index + 1
                parse_line(table_name, comment, key_level_map, line_level, index, lines, properties)

def extract_property_info(properties):
    rows = []
    for property_name, value in properties.items():
        if '#' in value[0]:
            value_parts = value[0].split('#')
            comment = value_parts[1]
        else:
            comment = value[1]
        pattern = r'\"\$\{(.*?)\:(.*?)\}\"'
        match = re.match(pattern, value[0])
        if match is not None:
            rows.append((property_name, match.group(1), match.group(2), comment, value[2]))
        else:
            rows.append((property_name, "", value[0].split('#')[0], comment, value[2]))
    return rows

def check_descriptions(properties):
    variables_without_description = []
    for row in properties:
        # Extract information from the tuple
        property_name, env_variable, default_value, comment, table_name = row
        if comment == '' or len(comment) < 5 :
            variables_without_description.append(property_name)

    return variables_without_description


def check_yml(total_list, input_yaml_file):
    # Parse yml file to map where key is property key path with '.' separator
    # and value is an object (env_name_with_default_value, comment, table_name)
    properties = extract_properties_with_comments(input_yaml_file)

    # Extract property information (extract env name, default value and comment nearby property)
    property_info = extract_property_info(properties)

    # Check all properties have descriptions
    variables_without_description = check_descriptions(property_info)
    total_list.extend(variables_without_description)
    if len(variables_without_description) > 0:
        print(f"Check {input_yaml_file}. There are some yml properties without valid description: (total {len(variables_without_description)}) {variables_without_description}.")

if __name__ == '__main__':
    sys.setrecursionlimit(10000)
    files_to_check = ["application/src/main/resources/thingsboard.yml",
                      "transport/http/src/main/resources/tb-http-transport.yml",
                      "transport/mqtt/src/main/resources/tb-mqtt-transport.yml",
                      "transport/coap/src/main/resources/tb-coap-transport.yml",
                      "transport/lwm2m/src/main/resources/tb-lwm2m-transport.yml",
                      "transport/snmp/src/main/resources/tb-snmp-transport.yml",
                      "msa/vc-executor/src/main/resources/tb-vc-executor.yml",
                      "integration/executor/src/main/resources/tb-integration-executor.yml"]

    total_list = []
    for file in files_to_check:
        check_yml(total_list, file)
    if len(total_list) > 0:
        exit(1)
    else:
        print("All yml properties have valid description.")