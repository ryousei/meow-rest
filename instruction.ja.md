## MEOW Network Controller　利用手順

### 推奨動作環境

- Linux CentOS 7.9
- ONOS 2.7.0
- Bazel 3.7.1
- Apache Maven 3.8.4
- OpenJDK 11.0.16 2022-07-19 LTS

### ONOS 2.7.0の入手と起動
#### ソースコードからビルドする場合

[github](https://github.com/opennetworkinglab/onos)からソースコードを入手、ビルドして起動する。

```sh
git clone https://github.com/opennetworkinglab/onos
cd onos
git checkout 2.7.0
bazel build onos
bazel run onos-local -- clean
```

#### バイナリを使用する場合

[ONOS公式ウェブページ](https://wiki.onosproject.org/display/ONOS/Downloads)からX-Wing (LTS)のビルド済みパッケージを入手する。パッケージを展開して起動する。

```sh
curl https://repo1.maven.org/maven2/org/onosproject/onos-releases/2.7.0/onos-2.7.0.tar.gz
tar x onos-2.7.0.tar.gz
bin/onos-service
```

### MEOW Network Controller (meow-rest)

#### meow-rest のビルド

```sh
git clone git@github.com:ryousei/meow-rest.git
cd meow-rest/meow-app
mvn clean install
```

#### meow-rest のONOSへのインストールとサービス開始

```sh
onos-app localhost reinstall! target/meow-app-1.0-SNAPSHOT.oar 
```
reinstallを使用しているので、初回は最初にエラーが出るが、無視して構わない。

#### 模擬EtherCATマスターの起動

meow-restの設定ファイルを読み込む前に、EtherCATマスターを起動する必要がある。
動作確認用に模擬EtherCATマスターを提供している。
EtherCATマスターのIPアドレスやポート番号は [master.json](meow-app/input/master.json) に定義してある。
以下の例では２つの光プレーンを制御するために２つの擬似EtherCATマスターを起動している。

```sh
gcc -o server meow-rest/meow-app/test/server.c
./server 54890
（次のコマンドは別ターミナルから実行する）
./server 54892
```

#### 光スイッチの設定ファイルの読込み

ここではサンプルスクリプト[test.sh](meow-app/test.sh)を用いる。

```sh
sh test.sh
```

```sh
$ cat test.sh
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
    sleep 10

    curl -u karaf:karaf -X DELETE $URL/gid/GID000
    echo
fi
```

冒頭のURL定義は、ONOSのmeow-restのサービスが提供しているURLに変更する必要がある。

[test.sh](meow-app/test.sh)から読込んでいる設定ファイルは以下の通り。

#### 設定ファイル

| file name                 | description |
| --- | --- |
| Full4x4_tpl_config.json   | src port, dst port を接続するのに必要な光空間スイッチ(osw) のポート接続と光波長スイッチ(lsw)の波長とポート設定。rv*が接頭語しているのは反対方向の設定。|
| lsw-device.json           | 光波長スイッチを制御するMEOWボードID(MasterID)とEtherCATマスターID(SubMasterID)とEtherCATループのスレーブID(SlaveID)の設定。test.shでは使わないので、中身は空。|
| osw-device.json           | 光空間スイッチを制御するMEOWボードID(MasterID)とEtherCATマスターID(SubMasterID)とEtherCATループのスレーブID(SlaveID)の設定。|
| master.json               | EtherCATマスターへのアクセス方法の定義、ポート番号とIPアドレス、ユーザ、パスワードの設定。|
| port.json                 |  光スイッチとラックの接続表 |

### 光パスの制御方法

以下では、4ラック構成の単純なデータセンターネットワークを例として、ラック間に光パスをセットアップする方法を示す。

![A simple data center diagram](images/fig1.png)

#### 光パストポロジ定義

４ノードをリング構成(-> k0001-001 -> k0002-001 -> k0004-001 -> k0003-001 -->)で接続するために、光パストポロジ定義ファイルを作成する。

```sh
$ cat input/REQ-4x4-ring-BI.json 
{"requests":[
    {"dir":"BI","src":"K0001","dst":" K0002","user":"meowadmin",
     "hosts":["K0001-001", "K0002-001"]},
    {"dir":"BI","src":"K0002","dst":" K0004","user":"meowadmin",
     "hosts":["K0002-001", "K0004-001"]},
    {"dir":"BI","src":"K0004","dst":" K0003","user":"meowadmin",
     "hosts":["K0004-001", "K0003-001"]},
    {"dir":"BI","src":"K0003","dst":" K0001","user":"meowadmin",
     "hosts":["K0003-001", "K0001-001"]}
]}
```

| パラメータ  | 説明 |
| --- | --- |
| dir       | BI: bi-direction, FW: forward direction, BW: backward direction |
| src, dst  |  接続元、接続先のラック 。K0000: 4桁のラック番号 |
| hosts     | セットアップしたパスを使用するホスト名。ラック番号-3桁のホスト番号 |
| user      | セットアップしたパスを使用するユーザ名 |

#### 光パスのセットアップ方法

REST-IFを使用して光パスのセットアップを要求する。

```sh
$ export URL=http://localhost:8181/onos/meow-app/controller
$ curl -u karaf:karaf -H "Content-Type: application/json" -d @input/REQ-4x4-ring-BI.json  -X POST $URL/setpaths
```

#### 光パスのティアダウン方法

REST-IFを使用して光パスのティアダウンを要求する。

```sh
$ curl -u karaf:karaf -X DELETE $URL/gid/GID000
{"OK:removePaths":"GID000 is remove now."}
```

### MPIプログラムの実行

次に追加した光パスを使用して、MPIでRing-based AllReduceベンチマークを実行する例を示す。

#### meow-restによる光パスのセットアップ

```sh
$ export URL=http://localhost:8181/onos/meow-app/controller
$ curl -u karaf:karaf -H "Content-Type: application/json" -d @input/REQ-4x4-ring-BI.json  -X POST $URL/setpaths
```

```sh
$ ssh k0001-001 cat hostlist4.ring
k0001-001
k0002-001
k0004-001
k0003-001
```

```sh
$ ssh K0001-001 mpirun --mca coll_base_verbose 1 --mca coll_tuned_use_dynamic_rules 1 --mca coll_tuned_allreduce_algorithm 4 -np 4 -pernode -hostfile hostlist4.ring osu/bin/osu_allreduce
# OSU MPI Allreduce Latency Test v5.7.1
# Size       Avg Latency(us)
4                      74.85
8                      69.71
16                    288.74
32                    290.08
64                    288.95
128                   287.59
256                   288.16
512                   289.64
1024                  289.88
2048                  309.25
4096                  510.29
8192                  830.78
16384                1100.55
32768                1320.32
65536                1629.05
131072               2549.42
262144               1042.91
524288               1469.63
1048576              2242.14
```

#### meow-restによる光パスのティアダウン

最後に追加したネットワークをティアダウン要求を出して削除する。

```sh
$ curl -u karaf:karaf -X DELETE $URL/gid/GID000
{"OK:removePaths":"GID000 is remove now."}
```
