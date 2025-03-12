///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import { AceHighlightRule } from '@shared/models/ace/ace.models';
import { TbEditorCompleter } from '@shared/models/ace/completion.models';

export const tbelUtilsAutocompletes = new TbEditorCompleter({
  btoa: {
    meta: 'function',
    description: 'Encodes a string to Base64.',
    args: [
      {
        name: 'str',
        description: 'The string to encode',
        type: 'string'
      }
    ],
    return: {
      description: 'The Base64 encoded string',
      type: 'string'
    }
  },
  atob: {
    meta: 'function',
    description: 'Decodes a Base64 encoded string.',
    args: [
      {
        name: 'str',
        description: 'The Base64 encoded string to decode',
        type: 'string'
      }
    ],
    return: {
      description: 'The decoded string',
      type: 'string'
    }
  },
  bytesToString: {
    meta: 'function',
    description: 'Converts a list of bytes to a string, optionally specifying the charset.',
    args: [
      {
        name: 'data',
        description: 'The list of bytes to convert',
        type: 'list'
      },
      {
        name: 'charsetName',
        description: 'The charset to use for conversion (e.g., "UTF-8")',
        type: 'string',
        optional: true
      }
    ],
    return: {
      description: 'The string representation of the bytes',
      type: 'string'
    }
  },
  decodeToString: {
    meta: 'function',
    description: 'Converts a list of bytes to a string.',
    args: [
      {
        name: 'data',
        description: 'The list of bytes to convert',
        type: 'list'
      }
    ],
    return: {
      description: 'The string representation of the bytes',
      type: 'string'
    }
  },
  decodeToJson: {
    meta: 'function',
    description: 'Parses a JSON string or converts a list of bytes to a string and parses it as JSON.',
    args: [
      {
        name: 'data',
        description: 'The JSON string or list of bytes to parse into JSON object',
        type: 'string | list'
      }
    ],
    return: {
      description: 'The parsed JSON object',
      type: 'object'
    }
  },
  stringToBytes: {
    meta: 'function',
    description: 'Converts a string to a list of bytes, optionally specifying the charset.',
    args: [
      {
        name: 'str',
        description: 'The string to convert',
        type: 'string'
      },
      {
        name: 'charsetName',
        description: 'The charset to use for conversion (e.g., "UTF-8")',
        type: 'string',
        optional: true
      }
    ],
    return: {
      description: 'The list of bytes representing the string',
      type: 'list'
    }
  },
  parseInt: {
    meta: 'function',
    description: 'Parses a string to an integer, optionally specifying the radix.',
    args: [
      {
        name: 'str',
        description: 'The string to parse',
        type: 'string'
      },
      {
        name: 'radix',
        description: 'The radix for parsing (e.g., 2 for binary, 16 for hex). If omitted, it is auto-detected (e.g., 0x for hex).',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The parsed integer, or null if invalid',
      type: 'number'
    }
  },
  parseLong: {
    meta: 'function',
    description: 'Parses a string to a long integer, optionally specifying the radix.',
    args: [
      {
        name: 'str',
        description: 'The string to parse',
        type: 'string'
      },
      {
        name: 'radix',
        description: 'The radix for parsing (e.g., 2 for binary, 16 for hex). If omitted, it is auto-detected (e.g., 0x for hex).',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The parsed long integer, or null if invalid',
      type: 'number'
    }
  },
  parseFloat: {
    meta: 'function',
    description: 'Parses a string to a float, optionally specifying the radix.',
    args: [
      {
        name: 'str',
        description: 'The string to parse',
        type: 'string'
      },
      {
        name: 'radix',
        description: 'The radix for parsing (e.g., 16 indicates a standard IEEE 754 hexadecimal float, defaults to 10 if unspecified)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The parsed float, or null if invalid',
      type: 'number'
    }
  },
  parseHexIntLongToFloat: {
    meta: 'function',
    description: 'Parses a hexadecimal string to a float, treating it as an integer value.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse (e.g., "0x0A" for 10.0)',
        type: 'string'
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret the string in big-endian order',
        type: 'boolean'
      }
    ],
    return: {
      description: 'The parsed float, or null if invalid',
      type: 'number'
    }
  },
  parseDouble: {
    meta: 'function',
    description: 'Parses a string to a double, optionally specifying the radix.',
    args: [
      {
        name: 'str',
        description: 'The string to parse',
        type: 'string'
      },
      {
        name: 'radix',
        description: 'The radix for parsing (e.g., 16 indicates a standard IEEE 754 double bits, defaults to 10 if unspecified)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The parsed double, or null if invalid',
      type: 'number'
    }
  },
  parseLittleEndianHexToInt: {
    meta: 'function',
    description: 'Parses a little-endian hexadecimal string to an integer.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed integer',
      type: 'number'
    }
  },
  parseBigEndianHexToInt: {
    meta: 'function',
    description: 'Parses a big-endian hexadecimal string to an integer.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed integer',
      type: 'number'
    }
  },
  parseHexToInt: {
    meta: 'function',
    description: 'Parses a hexadecimal string to an integer, optionally specifying endianness.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret the string in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed integer',
      type: 'number'
    }
  },
  parseBytesToInt: {
    meta: 'function',
    description: 'Parses a list or array of bytes to an integer.',
    args: [
      {
        name: 'data',
        description: 'The bytes to parse',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the byte list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bytes to parse (max 4)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret bytes in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed integer',
      type: 'number'
    }
  },
  parseLittleEndianHexToLong: {
    meta: 'function',
    description: 'Parses a little-endian hexadecimal string to a long integer.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed long integer',
      type: 'number'
    }
  },
  parseBigEndianHexToLong: {
    meta: 'function',
    description: 'Parses a big-endian hexadecimal string to a long integer.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed long integer',
      type: 'number'
    }
  },
  parseHexToLong: {
    meta: 'function',
    description: 'Parses a hexadecimal string to a long integer, optionally specifying endianness.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret the string in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed long integer',
      type: 'number'
    }
  },
  parseBytesToLong: {
    meta: 'function',
    description: 'Parses a list or array of bytes to a long integer.',
    args: [
      {
        name: 'data',
        description: 'The bytes to parse',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the byte list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bytes to parse (max 8)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret bytes in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed long integer',
      type: 'number'
    }
  },
  parseLittleEndianHexToFloat: {
    meta: 'function',
    description: 'Parses a little-endian hexadecimal string to a float using IEEE 754 format.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed float',
      type: 'number'
    }
  },
  parseBigEndianHexToFloat: {
    meta: 'function',
    description: 'Parses a big-endian hexadecimal string to a float using IEEE 754 format.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed float',
      type: 'number'
    }
  },
  parseHexToFloat: {
    meta: 'function',
    description: 'Parses a hexadecimal string to a float using IEEE 754 format, optionally specifying endianness.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret the string in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed float',
      type: 'number'
    }
  },
  parseBytesToFloat: {
    meta: 'function',
    description: 'Parses a list or array of bytes to a float using IEEE 754 format.',
    args: [
      {
        name: 'data',
        description: 'The bytes to parse',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the byte list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bytes to parse (max 4)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret bytes in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed float',
      type: 'number'
    }
  },
  parseBytesIntToFloat: {
    meta: 'function',
    description: 'Parses a list or array of bytes to a float by first interpreting them as an integer.',
    args: [
      {
        name: 'data',
        description: 'The bytes to parse',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the byte list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bytes to parse (max 4)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret bytes in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed float',
      type: 'number'
    }
  },
  parseLittleEndianHexToDouble: {
    meta: 'function',
    description: 'Parses a little-endian hexadecimal string to a double using IEEE 754 format.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed double',
      type: 'number'
    }
  },
  parseBigEndianHexToDouble: {
    meta: 'function',
    description: 'Parses a big-endian hexadecimal string to a double using IEEE 754 format.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      }
    ],
    return: {
      description: 'The parsed double',
      type: 'number'
    }
  },
  parseHexToDouble: {
    meta: 'function',
    description: 'Parses a hexadecimal string to a double using IEEE 754 format, optionally specifying endianness.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to parse',
        type: 'string'
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret the string in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed double',
      type: 'number'
    }
  },
  parseBytesToDouble: {
    meta: 'function',
    description: 'Parses a list or array of bytes to a double using IEEE 754 format.',
    args: [
      {
        name: 'data',
        description: 'The bytes to parse',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the byte list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bytes to parse (max 8)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret bytes in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed double',
      type: 'number'
    }
  },
  parseBytesLongToDouble: {
    meta: 'function',
    description: 'Parses a list or array of bytes to a double by first interpreting them as a long integer.',
    args: [
      {
        name: 'data',
        description: 'The bytes to parse',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the byte list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bytes to parse (max 8)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to interpret bytes in big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The parsed double',
      type: 'number'
    }
  },
  toFixed: {
    meta: 'function',
    description: 'Rounds a floating-point number to a set precision using half-up rounding.',
    args: [
      {
        name: 'value',
        description: 'The floating-point number',
        type: 'number'
      },
      {
        name: 'precision',
        description: 'The number of decimal places',
        type: 'number'
      }
    ],
    return: {
      description: 'The rounded floating-point number.',
      type: 'number'
    }
  },
  toInt: {
    meta: 'function',
    description: 'Converts a floating-point number to an integer by half-up rounding.',
    args: [
      {
        name: 'value',
        description: 'The floating-point number to convert',
        type: 'number'
      }
    ],
    return: {
      description: 'The rounded integer',
      type: 'number'
    }
  },
  hexToBytes: {
    meta: 'function',
    description: 'Converts a hexadecimal string to a list of bytes.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to convert',
        type: 'string'
      }
    ],
    return: {
      description: 'The list of bytes',
      type: 'list'
    }
  },
  hexToBytesArray: {
    meta: 'function',
    description: 'Converts a hexadecimal string to an array of bytes.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to convert',
        type: 'string'
      }
    ],
    return: {
      description: 'The array of bytes',
      type: 'array'
    }
  },
  intToHex: {
    meta: 'function',
    description: 'Converts an integer to a hexadecimal string.',
    args: [
      {
        name: 'value',
        description: 'The integer to convert',
        type: 'number'
      },
      {
        name: 'bigEndian',
        description: 'Whether to use big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      },
      {
        name: 'prefix',
        description: 'Whether to prefix with "0x" (defaults to false)',
        type: 'boolean',
        optional: true
      },
      {
        name: 'length',
        description: 'The desired length of the hex string (defaults to minimum required)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The hexadecimal string',
      type: 'string'
    }
  },
  longToHex: {
    meta: 'function',
    description: 'Converts a long integer to a hexadecimal string.',
    args: [
      {
        name: 'value',
        description: 'The long integer to convert',
        type: 'number'
      },
      {
        name: 'bigEndian',
        description: 'Whether to use big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      },
      {
        name: 'prefix',
        description: 'Whether to prefix with "0x" (defaults to false)',
        type: 'boolean',
        optional: true
      },
      {
        name: 'length',
        description: 'The desired length of the hex string (defaults to minimum required)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The hexadecimal string',
      type: 'string'
    }
  },
  intLongToRadixString: {
    meta: 'function',
    description: 'Converts a long integer to a string in the specified radix.',
    args: [
      {
        name: 'value',
        description: 'The number to convert',
        type: 'number'
      },
      {
        name: 'radix',
        description: 'The radix for conversion (e.g., 2 for binary, 16 for hex). If omitted, it defaults to 10.',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to use big-endian order for hex (defaults to true)',
        type: 'boolean',
        optional: true
      },
      {
        name: 'prefix',
        description: 'Whether to prefix hex with "0x" (defaults to false)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The string representation in the specified radix',
      type: 'string'
    }
  },
  floatToHex: {
    meta: 'function',
    description: 'Converts a float to its IEEE 754 hexadecimal representation.',
    args: [
      {
        name: 'value',
        description: 'The float to convert',
        type: 'number'
      },
      {
        name: 'bigEndian',
        description: 'Whether to use big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The hexadecimal string',
      type: 'string'
    }
  },
  doubleToHex: {
    meta: 'function',
    description: 'Converts a double to its IEEE 754 hexadecimal representation.',
    args: [
      {
        name: 'value',
        description: 'The double to convert',
        type: 'number'
      },
      {
        name: 'bigEndian',
        description: 'Whether to use big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The hexadecimal string',
      type: 'string'
    }
  },
  printUnsignedBytes: {
    meta: 'function',
    description: 'Converts a list of signed bytes to a list of unsigned integer values.',
    args: [
      {
        name: 'data',
        description: 'The list of bytes to convert',
        type: 'list'
      }
    ],
    return: {
      description: 'The list of unsigned integers (0-255)',
      type: 'list'
    }
  },
  base64ToHex: {
    meta: 'function',
    description: 'Converts a Base64 string to a hexadecimal string.',
    args: [
      {
        name: 'str',
        description: 'The Base64 string to convert',
        type: 'string'
      }
    ],
    return: {
      description: 'The hexadecimal string',
      type: 'string'
    }
  },
  hexToBase64: {
    meta: 'function',
    description: 'Converts a hexadecimal string to a Base64 string.',
    args: [
      {
        name: 'hex',
        description: 'The hexadecimal string to convert',
        type: 'string'
      }
    ],
    return: {
      description: 'The Base64 string',
      type: 'string'
    }
  },
  base64ToBytes: {
    meta: 'function',
    description: 'Converts a Base64 string to an array of bytes.',
    args: [
      {
        name: 'str',
        description: 'The Base64 string to convert',
        type: 'string'
      }
    ],
    return: {
      description: 'The array of bytes',
      type: 'array'
    }
  },
  base64ToBytesList: {
    meta: 'function',
    description: 'Converts a Base64 string to a list of bytes.',
    args: [
      {
        name: 'str',
        description: 'The Base64 string to convert',
        type: 'string'
      }
    ],
    return: {
      description: 'The list of bytes',
      type: 'list'
    }
  },
  bytesToBase64: {
    meta: 'function',
    description: 'Converts an array of bytes to a Base64 string.',
    args: [
      {
        name: 'data',
        description: 'The array of bytes to convert',
        type: 'array'
      }
    ],
    return: {
      description: 'The Base64 string',
      type: 'string'
    }
  },
  bytesToHex: {
    meta: 'function',
    description: 'Converts a list or array of bytes to a hexadecimal string.',
    args: [
      {
        name: 'data',
        description: 'The bytes to convert',
        type: 'list | array'
      }
    ],
    return: {
      description: 'The hexadecimal string',
      type: 'string'
    }
  },
  toFlatMap: {
    meta: 'function',
    description: 'Converts a nested map to a flat map, with customizable key paths and exclusions',
    args: [
      {
        name: 'json',
        description: 'The nested map to flatten',
        type: 'object'
      },
      {
        name: 'excludeKeys',
        description: 'List of keys to exclude from flattening',
        type: 'list',
        optional: true
      },
      {
        name: 'pathInKey',
        description: 'Whether to include full path in keys (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The flattened map',
      type: 'object'
    }
  },
  encodeURI: {
    meta: 'function',
    description: 'Encodes a URI string, preserving certain characters as per MDN standards.',
    args: [
      {
        name: 'str',
        description: 'The URI string to encode',
        type: 'string'
      }
    ],
    return: {
      description: 'The encoded URI string',
      type: 'string'
    }
  },
  decodeURI: {
    meta: 'function',
    description: 'Decodes a URI string previously encoded.',
    args: [
      {
        name: 'str',
        description: 'The URI string to decode',
        type: 'string'
      }
    ],
    return: {
      description: 'The decoded URI string',
      type: 'string'
    }
  },
  raiseError: {
    meta: 'function',
    description: 'Throws an error with a custom message.',
    args: [
      {
        name: 'str',
        description: 'The error message to throw',
        type: 'string'
      }
    ],
    return: {
      description: 'Does not return; throws an exception',
      type: 'void'
    }
  },
  isBinary: {
    meta: 'function',
    description: 'Checks if a string is a binary number.',
    args: [
      {
        name: 'str',
        description: 'The string to check',
        type: 'string'
      }
    ],
    return: {
      description: '2 if the string is binary, -1 otherwise',
      type: 'number'
    }
  },
  isOctal: {
    meta: 'function',
    description: 'Checks if a string is an octal number.',
    args: [
      {
        name: 'str',
        description: 'The string to check',
        type: 'string'
      }
    ],
    return: {
      description: '8 if the string is octal, -1 otherwise',
      type: 'number'
    }
  },
  isDecimal: {
    meta: 'function',
    description: 'Checks if a string is a decimal number.',
    args: [
      {
        name: 'str',
        description: 'The string to check',
        type: 'string'
      }
    ],
    return: {
      description: '10 if the string is decimal, -1 otherwise',
      type: 'number'
    }
  },
  isHexadecimal: {
    meta: 'function',
    description: 'Checks if a string is a hexadecimal number.',
    args: [
      {
        name: 'str',
        description: 'The string to check',
        type: 'string'
      }
    ],
    return: {
      description: '16 if the string is hexadecimal, -1 otherwise',
      type: 'number'
    }
  },
  bytesToExecutionArrayList: {
    meta: 'function',
    description: 'Converts an array of bytes to a list.',
    args: [
      {
        name: 'data',
        description: 'The array of bytes to convert',
        type: 'array'
      }
    ],
    return: {
      description: 'The list of bytes',
      type: 'list'
    }
  },
  padStart: {
    meta: 'function',
    description: 'Pads the start of a string with a character until it reaches the target length.',
    args: [
      {
        name: 'str',
        description: 'The string to pad',
        type: 'string'
      },
      {
        name: 'length',
        description: 'The desired length of the resulting string',
        type: 'number'
      },
      {
        name: 'padString',
        description: 'The character to pad with (single character)',
        type: 'string'
      }
    ],
    return: {
      description: 'The padded string',
      type: 'string'
    }
  },
  padEnd: {
    meta: 'function',
    description: 'Pads the end of a string with a character until it reaches the target length.',
    args: [
      {
        name: 'str',
        description: 'The string to pad',
        type: 'string'
      },
      {
        name: 'length',
        description: 'The desired length of the resulting string',
        type: 'number'
      },
      {
        name: 'padString',
        description: 'The character to pad with (single character)',
        type: 'string'
      }
    ],
    return: {
      description: 'The padded string',
      type: 'string'
    }
  },
  parseByteToBinaryArray: {
    meta: 'function',
    description: 'Converts a byte to a binary array.',
    args: [
      {
        name: 'value',
        description: 'The byte value to convert',
        type: 'number'
      },
      {
        name: 'length',
        description: 'The length of the binary array (defaults to 8)',
        type: 'number',
        optional: true
      },
      {
        name: 'bigEndian',
        description: 'Whether to use big-endian order (defaults to true)',
        type: 'boolean',
        optional: true
      }
    ],
    return: {
      description: 'The binary array',
      type: 'array'
    }
  },
  parseBytesToBinaryArray: {
    meta: 'function',
    description: 'Converts a list or array of bytes to a binary array.',
    args: [
      {
        name: 'data',
        description: 'The bytes to convert',
        type: 'list | array'
      },
      {
        name: 'length',
        description: 'The total length of the binary array (defaults to bytes.length * 8)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The binary array',
      type: 'array'
    }
  },
  parseLongToBinaryArray: {
    meta: 'function',
    description: 'Converts a long integer to a binary array.',
    args: [
      {
        name: 'value',
        description: 'The long integer to convert',
        type: 'number'
      },
      {
        name: 'length',
        description: 'The length of the binary array (defaults to 64)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The binary array',
      type: 'array'
    }
  },
  parseBinaryArrayToInt: {
    meta: 'function',
    description: 'Converts a binary list or array to an integer.',
    args: [
      {
        name: 'data',
        description: 'The binary list or array to convert',
        type: 'list | array'
      },
      {
        name: 'offset',
        description: 'The starting index in the binary list or array (defaults to 0)',
        type: 'number',
        optional: true
      },
      {
        name: 'length',
        description: 'The number of bits to parse (defaults to array length)',
        type: 'number',
        optional: true
      }
    ],
    return: {
      description: 'The parsed integer',
      type: 'number'
    }
  }
});

const tbelUtilsFuncNames = [
  "btoa",
  "atob",
  "bytesToString",
  "decodeToString",
  "decodeToJson",
  "stringToBytes",
  "parseInt",
  "parseLong",
  "parseFloat",
  "parseHexIntLongToFloat",
  "parseDouble",
  "parseLittleEndianHexToInt",
  "parseBigEndianHexToInt",
  "parseHexToInt",
  "parseBytesToInt",
  "parseLittleEndianHexToLong",
  "parseBigEndianHexToLong",
  "parseHexToLong",
  "parseBytesToLong",
  "parseLittleEndianHexToFloat",
  "parseBigEndianHexToFloat",
  "parseHexToFloat",
  "parseBytesToFloat",
  "parseBytesIntToFloat",
  "parseLittleEndianHexToDouble",
  "parseBigEndianHexToDouble",
  "parseHexToDouble",
  "parseBytesToDouble",
  "parseBytesLongToDouble",
  "toFixed",
  "toInt",
  "hexToBytes",
  "hexToBytesArray",
  "intToHex",
  "longToHex",
  "intLongToRadixString",
  "floatToHex",
  "doubleToHex",
  "printUnsignedBytes",
  "base64ToHex",
  "hexToBase64",
  "base64ToBytes",
  "base64ToBytesList",
  "bytesToBase64",
  "bytesToHex",
  "toFlatMap",
  "encodeURI",
  "decodeURI",
  "raiseError",
  "isBinary",
  "isOctal",
  "isDecimal",
  "isHexadecimal",
  "bytesToExecutionArrayList",
  "padStart",
  "padEnd",
  "parseByteToBinaryArray",
  "parseBytesToBinaryArray",
  "parseLongToBinaryArray",
  "parseBinaryArrayToInt"
];

export const tbelUtilsFuncHighlightRules: Array<AceHighlightRule> =
  tbelUtilsFuncNames.map(funcName => ({
    token: 'tb.tbel-utils-func',
    regex: `\\b${funcName}\\b`,
    next: 'no_regex'
  }));
