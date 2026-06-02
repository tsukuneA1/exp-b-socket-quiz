# GuiClient.java のGUI起動方法

## 概要

`apps/GuiClient.java` は、既存のCUIクライアントである `apps/Client.java` を残したまま追加したGUI版クライアントである。

通信プロトコルはCUI版と同じものを使用しているため、サーバ側の `apps/server/`、ゲームロジック側の `apps/game/`、共通プロトコル側の `apps/shared/` は変更しない。

## コンパイル

CUI版とGUI版でコンパイル方法は同じである。

```bash
mvn compile
```

Makefileを使う場合は以下を実行する。

```bash
make build
```

## 起動方法

最初にサーバを起動する。

```bash
mvn exec:java -Dexec.mainClass=Server
```

Makefileを使う場合は以下を実行する。

```bash
make server
```

次に、別ターミナルでGUIクライアントを起動する。

```bash
mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="Alice"
```

Makefileを使う場合は以下を実行する。

```bash
make gui-client name=Alice
```

ポート番号を指定してサーバを起動した場合は、GUIクライアント側にも同じポート番号を渡す。

```bash
mvn exec:java -Dexec.mainClass=Server -Dexec.args=9090
mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="Alice 9090"
```

Makefileを使う場合は以下を実行する。

```bash
make server-port port=9090
make gui-client-port name=Alice port=9090
```

## CUI版との違い

CUI版は `Client`、GUI版は `GuiClient` を指定して起動する。

```bash
# CUI版
mvn exec:java -Dexec.mainClass=Client -Dexec.args="Alice"

# GUI版
mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="Alice"
```

コンパイル対象は同じなので、GUI版を追加しても `mvn compile` のコマンドは変わらない。

