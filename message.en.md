## MEOW Message Format

This document defines the request and response message format between MEOW network controller and MEOW device controller.

A message consists of the header and one or more control parameters.
A response is corresponding to a request by the request ID in each control parameter.

### Parameter Table (Request version 3)

Request Header:

| Half Word  | Type   | Name        | Value | Description |
| ---------- | ------ | -------     | ----- | ----------- |
| 0          | UINT16 | Version     | 3     | Version 3   |
| 1          | UINT16 | Request Num | 0-    | Number of control parameters in this request  |
| 2          | UINT16 | Request ID  | 0-    | Sequence number indicating the order of request packet's sent |
| 3          | UINT16 | Reserved    | 0     |              |
 
Request Control Parameters:

| Half Word  | Type   | Name         | Value | Description |
| ---------- | ------ | -------      | ----- | ----------- |
| 0          | UINT16 | Parameter ID | 0-    | Sequence number indicating the ordinal number of control parameters |
| 1          | UINT16 | Type         | 1, 2  | Device Type: Optical Wavelength Transmitter/Receiver (1), Optical Core Switch (2) |
| 2          | UINT16 | Device ID    | 0-    | Slave ID (device ID) in EtherCAT loop |
| 3          | UINT16 | Command      | 1-    | Commands: 1:setup, 2:teardown, 3:getStatus,..  (getStatus requests the master's register information) |
| 4          | UINT16 | Channel/DstPort | 0- | Optical Wavelength Transmitter/Receiver: Wavelength Number, Optical Core Switch: Output Port Number |
| 5          | UINT16 | SrcPort      | 0-    | Optical Wavelength Transmitter/Receiver: 0, Optical Core Switch: Input Port Number |
| 6-7        | UINT16 | Reserved     | 0     |              |

### Parameter Table (Response version 3)

Response Header:

| Half Word  | Type   | Name        | Value | Description |
| ---------- | ------ | -------     | ----- | ----------- |
| 0          | UINT16 | Version     | 3     | Version 3   |
| 1          | UINT16 | Request Num | 0-    | Number of control parameters in this response  |
| 2          | UINT16 | Request ID  | 0-    | Sequence number indicating the order of response packet's sent |
| 3          | UINT16 | Reserved    | 0     |              |
 
Request Response Parameters:

| Half Word  | Type   | Name         | Value | Description |
| ---------- | ------ | -------      | ----- | ----------- |
| 0          | UINT16 | Parameter ID | 0-    | Sequence number indicating the ordinal number of Response parameters |
| 1          | UINT16 | Error Cord   | 0-    | Error code: indicates normal end(0) or abnormal end(1: Timeout, 2: , )  |
| 2-7        | UINT16 | Status       | 0-    | The meaning of the value varies depending on the Command |

Status:
- If Command is setup(1) or teardown(2), this filled with 0.
- If Command is getStatus(3), the error information is represented by one bit per slave. 0 means normal, 1 means other errors.
