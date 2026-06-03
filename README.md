# ソフトウェア制作 グループ制作プロジェクト

## 制作予定のもの

リアルタイム早押しクイズ

複数人のクライアントがサーバに接続し、サーバが問題出題・早押し判定・回答受付・得点管理などのゲーム進行を行うネットワークアプリケーションを制作する。

## 課題の目的

本課題の目的は、Javaを用いたネットワークプログラミングを実体験し、TCP/IP、IPアドレス、ホスト名、ポート番号、ソケット通信の基礎を理解することである。

また、グループで計画を立て、ゴールを設定し、役割分担を行い、進捗報告・最終発表・レポートを通して、制作過程と成果を説明できるようにすることも求められている。

## 制作物の制約・条件

- Javaを使用する。
- socketを使用してネットワーク通信を行う。
- 制作するプログラムの形態は自由。
  - 例: サーバ・クライアント型、P2P型、分散並列処理型など。
- 最終発表では、自分のPC上で複数のプログラムを同時に動作させる。
- デモではローカルのTCP/IP機能を使い、ホスト名 `localhost` を用いる。
- 発表ではプロジェクト概要、プログラムの説明、簡単なデモを行う。
- グループメンバー全員が発表に参加する。

## 役割分担

| 担当者 | 主担当 | 目的 |
|---|---|---|
| A | サーバ・ゲーム進行担当 | ゲーム全体を成立させる |
| B | 通信・同期制御担当 | 複数人接続と早押し判定を安定させる |
| C | クライアント・テスト・機能補助担当 | CUI操作、テスト、問題データ、デモ確認を担当する |

## Build & Run

### コンパイル

CUI版とGUI版はどちらも同じコマンドでコンパイルする。

```bash
mvn compile
```

Makefileを使う場合:

```bash
make build
```

### サーバ起動

```bash
mvn exec:java -Dexec.mainClass=Server
```

ポート番号を指定する場合:

```bash
mvn exec:java -Dexec.mainClass=Server -Dexec.args=9090
```

Makefileを使う場合:

```bash
make server
make server-port port=9090
```

### CUIクライアント起動

```bash
mvn exec:java -Dexec.mainClass=Client -Dexec.args="Alice"
```

ポート番号を指定する場合:

```bash
mvn exec:java -Dexec.mainClass=Client -Dexec.args="Alice 9090"
```

Makefileを使う場合:

```bash
make client name=Alice
make client-port name=Alice port=9090
```

### GUIクライアント起動

```bash
mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="Alice"
```

ポート番号を指定する場合:

```bash
mvn exec:java -Dexec.mainClass=GuiClient -Dexec.args="Alice 9090"
```

Makefileを使う場合:

```bash
make gui-client name=Alice
make gui-client-port name=Alice port=9090
```

GUI版の詳細は [docs/client-gui.md](docs/client-gui.md) を参照する。

### テスト・フォーマット

```bash
make test       # ユニットテスト実行
make fmt        # コードフォーマット適用 (google-java-format)
make lint       # フォーマットチェックのみ
```

### 観測ツール（EventBus）

サーバーはポート9090でEventBusを起動する。observe.pyを別ターミナルで起動すると、早押し判定のタイムスタンプ（recv→enqueue→dequeue）をリアルタイムで可視化できる。

```bash
make observe
```

race.pyは複数のボットを同時接続させて早押しレースをシミュレートする。

```bash
# ボット2体が同時に正解を送信（observe.pyと組み合わせて使う）
python cli/race.py --bots 2 --answer 2

# --stagger-ms でボットごとに意図的な時差をつける
python cli/race.py --bots 2 --answer 2 --stagger-ms 50
```
