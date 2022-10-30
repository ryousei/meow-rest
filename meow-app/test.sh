#! /bin/bash
#
#  Copyright (c) 2022 National Institute of Advanced Industrial Science 
#  and Technology (AIST). All rights reserved.
# 
URL=http://localhost:8181/onos/meow-app/controller

curl -u karaf:karaf -X GET $URL/disconnect
echo
#
if [ "YES" = "YES" ]; then
    onos-app localhost reinstall! target/meow-app-1.0-SNAPSHOT.oar 
    # onos-app localhost install target/meow-app-1.0-SNAPSHOT.oar 
    sleep 1
fi

if [ "YES" = "YES" ]; then
    curl -u karaf:karaf -H "Content-Type: application/json" -d @input/Full4x4_tpl_config.json -X POST $URL/table5
    echo
    sleep 1
fi

curl -u karaf:karaf -H "Content-Type: application/json" -d @input/lsw-device.json  -X POST $URL/table5
echo
sleep 1

curl -u karaf:karaf -H "Content-Type: application/json" -d @input/osw-device.json  -X POST $URL/table5
echo
sleep 1

curl -u karaf:karaf -H "Content-Type: application/json" -d @input/master.json  -X POST $URL/table5
echo
sleep 1

curl -u karaf:karaf -H "Content-Type: application/json" -d @input/port.json  -X POST $URL/table5
echo
sleep 1

if [ "YES" = "NO" ]; then
    curl -u karaf:karaf -H "Content-Type: application/json" -d @input/REQ-4x4-ring-BI.json  -X POST $URL/setpaths
    echo
    sleep 7

    curl -u karaf:karaf -X DELETE $URL/gid/GID000
    echo
fi
