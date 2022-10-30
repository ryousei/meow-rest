## MEOW Network Controller: 4x4スイッチ構成の詳細説明

### ネットワーク構成

#### データセンタ内の構成図

各ToRスイッチ間の接続は、常に電気スイッチ00のレイヤ3パケットスイッチングで接続されています。

![A simple data center diagram](images/fig1.png)

初期状態では、光スイッチ01、02のパスはどこにも接続されていません。要求に応じて、光スイッチ01は、
ToRスイッチ間を接続する光パス01-02、01-03、01-04、02-03、02-04、03-04を接続したり、切り離したりすることができる。
同様に、光スイッチ02は、ToRスイッチ間を接続する光パス01-02、01-03、01-04、02-03、02-04、03-04を接続したり、切り離したりすることができる。

#### 電気スイッチで転送するか、光スイッチで転送するかの制御

光パスを利用するには、光スイッチのポート間を接続して、ToRスイッチで対象パケットを光スイッチに方に転送することが必要です。

この操作の指揮をとるのがMEOWネットワークコントローラです。下記のような手順で光パスにパケットを転送します。

1. 使用する光スイッチのポートをロックします。
2. 光パスのセットアップをMEOW EtherCATマスターに要求を出します。
3. 各ToRスイッチで使用する光パスが全部リンクアップしたことを確認します。
4. 対象となるラック内のノードでパケットにToS値を設定する指示を送ります。
5. ToRスイッチのOpenFlowの機能により、電気スイッチ向かうパケットの中でToSが設定されているパケットを光パスの方に切替ます。

逆に、光パスが必要なくなれば、下記の手順で転送を電気スイッチに戻します。
1. 各ノードに対象パケットのToS値を設定を削除する指示を送ります。 データセンター内の関係するノードでToSの設定を削除できたことの同期をとります。
2. ToRスイッチはToS値がつかなくなったパケットを電気スイッチでL3ルーティングします。
3. 光パスのティアダウンをMEOW EtherCATマスターに要求を出します。
4. 各ToRスイッチで使用する光パスが全部ティアダウンしたことを確認します。
5. 使用していた光スイッチのポートのロックを解放します

パケットのルーティングの変更は各ToRスイッチのOpenFlowで行います。OpenFlowのルールは光パスには関係なく固定です。

複数の光スイッチ01, 02で接続されたToRスイッチでは、ToS値の値によって、光スイッチ01にルーティングするか
光スイッチ02にルーティングするかの制御を行います。

#### 疑似EtherCATマスターを使用する場合

疑似EtherCATマスターを使用する場合には光スイッチの制御ができません。各光スイッチで光パスの制御を手動で行います。