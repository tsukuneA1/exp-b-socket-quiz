# AGENTS.md

This file provides guidance to AI coding agents working in this repository.

## Build & Run

Mavenを使う。`pom.xml` はプロジェクトルートにある。Java 21 必須（sealed interface・record・switch式・パターンマッチングswitch を使用）。

```bash
# コンパイル
mvn compile

# サーバー起動（ポート省略時は 8080）
mvn exec:java -Dexec.mainClass=Server
mvn exec:java -Dexec.mainClass=Server -Dexec.args=9090

# クライアント起動（別ターミナルで、引数: playerName [port]）
mvn exec:java -Dexec.mainClass=Client -Dexec.args="Alice"
mvn exec:java -Dexec.mainClass=Client -Dexec.args="Alice 9090"

# テスト実行
mvn test
```

`target/` と `.class` ファイルは `.gitignore` で除外済みのためコミットしない。

## アーキテクチャ

**現状**: バイナリフレーミングで CONNECT（playerName付き）/ CONNECT_ACK のマルチクライアント接続が動作している。満員時は CONNECT_NG を返す。

**目標**: リアルタイム早押しクイズ。複数クライアントがサーバーに接続し、サーバーが問題出題・早押し判定・得点管理を行う。

設計の詳細は [docs/design/2026-05-03-communication-sync.md](docs/design/2026-05-03-communication-sync.md) を参照。

### パッケージ構成と担当領域

```
apps/
  Client.java              エントリポイント（担当C）
  Server.java              エントリポイント（担当B）
  game/                    ゲームロジック層（担当A の作業場）
    GameConfig.java        定数（MIN_PLAYERS=2, MAX_PLAYERS=4）
    LobbyManager.java      セッション管理・ID採番・満員判定
  server/                  サーバー側インフラ層（担当B の作業場）
    ClientSession.java     per-clientスレッド（CONNECT〜DISCONNECT を処理）
  shared/                  プロトコル定義（担当B が管理・変更前に要確認）
    codec/
      MessageType.java     type バイト定数（C→S: 0x01-0x03、S→C: 0x11-0x18）
      FrameDecoder.java    readFrame() / decodeClient() / decodeServer()
      FrameEncoder.java    writeFrame()
      InvalidMessageException.java
    c2s/                   Client→Server メッセージ
      ClientMessage.java   sealed interface
      ConnectMessage(playerName), AnswerMessage, DisconnectMessage
    s2c/                   Server→Client メッセージ
      ServerMessage.java   sealed interface
      ConnectAckMessage, ConnectNgMessage, QuestionOptionsMessage,
      QuestionChunkMessage, WrongAnswerMessage, RoundEndMessage,
      ScoreMessage, DisconnectAckMessage, ScoreEntry
```

### 通信プロトコル

```
| type (1 byte) | body_length (4 bytes, big-endian) | body (n bytes) |
```

各メッセージクラスは `parse(byte[] body)` でデコード、`toBytes()` でボディをエンコードする。
`FrameDecoder` / `FrameEncoder` は `DataInputStream` / `DataOutputStream` を直接受け取る。

`shared/` 配下のファイルは担当B が管理する。追加・変更が必要な場合は担当B に確認すること。

### スレッドモデル

クライアント1人につき1スレッド（`ClientSession`）を立てる構成は実装済み。
現状は CONNECT→CONNECT_ACK・ANSWER受信ログ・DISCONNECT→DISCONNECT_ACK まで動作する。

次のステップ（未実装）: 受信メッセージを `BlockingQueue<ClientMessage>` に積み、ゲームロジックスレッド（`GameEngine`、担当A）がキューからデキューして処理する。担当Aは `FrameEncoder` / `FrameDecoder` を直接呼ばず、担当Bが提供するAPIを通じて送受信する。

## 役割分担

| 担当 | 責務 | 作業場 |
|---|---|---|
| A | サーバー側ゲームロジック（問題出題・得点管理） | `apps/game/` |
| B | フレーミング・マルチクライアント接続・早押し同期制御 | `apps/server/`・`apps/shared/` |
| C | クライアントCUI・テスト・デモ確認 | `apps/Client.java` |
