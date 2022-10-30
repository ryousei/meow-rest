# MEOW Network Controller

Copyright (c) 2022 National Institute of Advanced Industrial Science and Technology (AIST), All Rights Reserved.

## Overview

Multi-master EtherCAT-based control plane for Optical netWorks (MEOW) is a fast control plane mechanism based on an industrial Ethernet standard EtherCAT technology for operating optical switches at datacenter scale.

MEOW network controller is a part of the MEOW system based leveraging ONOS SDN controller framework and it provides three functionalities:

1. Northbound REST API for users/applications.
2. Southbound API for controlling optical switches by using EtherCAT.
3. Path finder for optical switch network. 

For more information, please check out our [publications](#publications).

## Getting started

See [Instruction](instruction.en.md) [[ja](instruction.ja.md)].

Further information:
- [MEOW message format](message.en.md)

## Publications

- Ryousei Takano, Toshiyuki Shimizu, Kiyo Ishii, Fumihiro Okazaki, Takahiro Hirofuchi, Shu Namiki, Ken-ichi Sato, "Demonstration of Multimaster EtherCAT-based control plane for Optical netWorks (MEOW)", 2021 Open Compute Project Future Technologies Symposium, November 2021.

## License

MEOW network controller is released under the Apache License 2.0.

## Acknowledgement

This source code is based on results obtained from a project, JPNP16007, commissioned by the New Energy and Industrial Technology Development Organization (NEDO).
