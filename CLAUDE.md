# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Mavenを使う。`pom.xml` はプロジェクトルートにある。Java 17 必須（sealed interface・record・switch式を使用）。

```bash
# コンパイル
mvn compile

# サーバー起動（ポート省略時は 8080）
mvn exec:java -Dexec.mainClass=apps.Server
mvn exec:java -Dexec.mainClass=apps.Server -Dexec.args=9090

# クライアント起動（別ターミナルで）
mvn exec:java -Dexec.mainClass=apps.Client
mvn exec:java -Dexec.mainClass=apps.Client -Dexec.args=9090

# テスト実行
mvn test
```

`target/` と `.class` ファイルは `.gitignore` で除外済みのためコミットしない。

## アーキテクチャ

**現状**: バイナリフレーミングで CONNECT/CONNECT_ACK の1対1ハンドシェイクが動作している。

**目標**: リアルタイム早押しクイズ。複数クライアントがサーバーに接続し、サーバーが問題出題・早押し判定・得点管理を行う。

設計の詳細は [docs/design/2026-05-03-communication-sync.md](docs/design/2026-05-03-communication-sync.md) を参照。

### パッケージ構成

```
apps/
  Client.java              エントリポイント
  Server.java              エントリポイント（nextPlayerId を AtomicInteger で採番）
  shared/
    codec/                 フレーミング層
      MessageType.java     type バイト定数（C→S: 0x01-0x03、S→C: 0x11-0x17）
      FrameDecoder.java    readFrame() / decodeClient() / decodeServer()
      FrameEncoder.java    writeFrame()
      InvalidMessageException.java
    c2s/                   Client→Server メッセージ（両側が参照する）
      ClientMessage.java   sealed interface
      ConnectMessage, AnswerMessage, DisconnectMessage
    s2c/                   Server→Client メッセージ（両側が参照する）
      ServerMessage.java   sealed interface
      ConnectAckMessage, QuestionOptionsMessage, QuestionChunkMessage,
      WrongAnswerMessage, RoundEndMessage, ScoreMessage,
      DisconnectAckMessage, ScoreEntry
```

### 通信プロトコル

```
| type (1 byte) | body_length (4 bytes, big-endian) | body (n bytes) |
```

各メッセージクラスは `parse(byte[] body)` でデコード、`toBytes()` でボディをエンコードする。
`FrameDecoder` / `FrameEncoder` は `DataInputStream` / `DataOutputStream` を直接受け取る。

### スレッドモデル（実装予定）

クライアント1人につき1スレッド（`ClientSession`）を立て、受信メッセージを `BlockingQueue<ClientMessage>` に積む。ゲームロジックスレッド（`GameEngine`、担当A）がキューからデキューして処理する。

## 役割分担

| 担当 | 責務 |
|---|---|
| A | サーバー側ゲームロジック（問題出題・得点管理） |
| B | フレーミング・マルチクライアント接続・早押し同期制御 |
| C | クライアントCUI・テスト・デモ確認 |
