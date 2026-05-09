# B担当 設計メモ：通信・同期制御

担当者: B  
最終更新: 2026-05-04

---

## 1. 担当範囲

複数クライアントの同時接続管理、アプリケーション層のメッセージフレーミング設計、早押し判定の競合制御を担当する。  
A（ゲームロジック）とC（クライアントUI・テスト）が依存するメッセージ仕様とスレッドモデルを定義する。

型定数の実装: [apps/shared/MessageType.java](../../apps/shared/MessageType.java)

---

## 2. 設計方針

### 2-1. フレーミング仕様

TCPはバイトストリームのため、メッセージの区切りをアプリ層で定義する必要がある。  
以下の固定ヘッダー + 可変ボディ構造を採用する。

```
| type (1 byte) | body_length (4 bytes, big-endian) | body (n bytes) |
```

#### C → S メッセージ

| 定数名 | 値 | ボディ | 備考 |
|---|---|---|---|
| `CONNECT` | `0x01` | なし | 接続通知。プレイヤー名はサーバーが採番 |
| `ANSWER` | `0x02` | 1 byte（選択肢番号 0–3） | 早押し＋回答を兼ねる |
| `DISCONNECT` | `0x03` | なし | 正常切断 |

#### S → C メッセージ

| 定数名 | 値 | ボディ | 送信先 | 備考 |
|---|---|---|---|---|
| `CONNECT_ACK` | `0x11` | 1 byte（採番ID） | 本人のみ | |
| `QUESTION` | `0x12` | JSON（問題文＋選択肢） | 全員 | ラウンド開始 |
| `WRONG_ANSWER` | `0x13` | なし | 本人のみ | 外れ。ラウンド継続中 |
| `ROUND_END` | `0x14` | JSON（勝者ID・正解番号） | 全員 | ラウンド終了 |
| `SCORE` | `0x15` | JSON（全員のスコア） | 全員 | ROUND_END直後に送信 |
| `DISCONNECT_ACK` | `0x16` | なし | 本人のみ | |

`int` を使う理由: `DataInputStream.read()` は int を返すため、byte だと符号拡張の比較バグが起きやすい。

#### QUESTIONボディのJSON例

```json
{"question": "日本の首都は？", "options": ["大阪", "京都", "東京", "名古屋"]}
```

#### ROUND_ENDボディのJSON例

```json
{"winner_id": 1, "correct_index": 2}
```

---

### 2-2. スレッドモデル

```mermaid
flowchart TD
    Main["main thread\nServerSocket.accept() ループ"]
    T1["ClientHandler\nThread 1"]
    T2["ClientHandler\nThread 2"]
    T3["ClientHandler\nThread 3"]
    Queue["BlockingQueue&lt;Message&gt;\n受信メッセージキュー"]
    Game["Game Thread\nゲーム進行 ※A担当"]

    Main -->|"new Thread(socket)"| T1
    Main -->|"new Thread(socket)"| T2
    Main -->|"new Thread(socket)"| T3
    T1 -->|"queue.put()"| Queue
    T2 -->|"queue.put()"| Queue
    T3 -->|"queue.put()"| Queue
    Queue -->|"queue.take()"| Game
```

各クライアントに1スレッドを割り当て、受信したメッセージを `BlockingQueue` に積む。  
ゲームロジック（A担当）はキューからデキューして処理する。スレッド間の直接メソッド呼び出しは避ける。

---

### 2-3. 接続・ゲーム進行のシーケンス

```mermaid
sequenceDiagram
    participant C1 as Client 1
    participant C2 as Client 2
    participant S as Server

    C1->>S: TCP connect
    C2->>S: TCP connect
    S->>C1: CONNECT_ACK (id=1)
    S->>C2: CONNECT_ACK (id=2)

    S->>C1: QUESTION
    S->>C2: QUESTION

    C1->>S: ANSWER(2)
    C2->>S: ANSWER(3)（わずかに後）

    Note over S: synchronized ブロック内で<br/>最初のANSWERのみ受理

    alt C1が正解
        S->>C1: ROUND_END (winner=1, correct=2)
        S->>C2: ROUND_END (winner=1, correct=2)
        S->>C1: SCORE
        S->>C2: SCORE
    else C1が不正解
        S->>C1: WRONG_ANSWER
        Note over S,C2: ラウンド継続。C2はまだ回答可能
        C2->>S: ANSWER(2)
        S->>C1: ROUND_END (winner=2, correct=2)
        S->>C2: ROUND_END (winner=2, correct=2)
        S->>C1: SCORE
        S->>C2: SCORE
    end
```

---

### 2-4. 早押し同期ロジック

複数の ClientHandler スレッドが同時に `ANSWER` を受信したとき、最初の1人だけを受理する。

```java
// GameState（A担当クラスに持たせる想定）
private final Object answerLock = new Object();
private boolean answered = false;
private int winnerId = -1;

public boolean tryAnswer(int clientId) {
    synchronized (answerLock) {
        if (!answered) {
            answered = true;
            winnerId = clientId;
            return true;
        }
        return false;
    }
}
```

不正解だった場合は `answered` を `false` に戻すことでラウンドを継続する。

---

### 2-5. 切断ハンドリング

クライアントが突然切断すると `read()` が `-1` または `IOException` を返す。  
各 ClientHandler スレッドで必ずキャッチし、コネクションリストから除去してソケットをクローズする。

```java
try {
    // 受信ループ
} catch (IOException e) {
    // 切断扱い
} finally {
    clients.remove(this);
    socket.close();
}
```

---

## 3. 実装マイルストーン

| フェーズ | 実装内容 | 理解目標 |
|---|---|---|
| 1 | 現状のecho通信を読んで動かす | TCPコネクション確立・`readLine()`が改行区切りフレーミングであることを理解する |
| 2 | バイナリフレーミング実装（type+length+body） | TCPがストリームである意味・length-prefixedフレーミングの必要性 |
| 3 | マルチクライアント対応（スレッド追加） | スレッドのライフサイクル・共有オブジェクトへのアクセス競合 |
| 4 | `BlockingQueue` によるスレッド間通信 | Producer-Consumerパターン・スレッドをまたぐ安全なデータ受け渡し |
| 5 | `synchronized` で早押し判定 | race condition・`synchronized` と `volatile` の違い |
| 6 | 切断ハンドリング | `IOException`・リソースリーク・`finally` によるクローズ保証 |
| 7 | （発展）アプリ層ハートビート（PING/PONG） | TCP keepaliveとの違い・アプリ層での死活監視 |
| 8 | （発展）NIO + `Selector` に置き換え | I/O多重化・ノンブロッキングI/O・Node.jsのイベントループとの対比 |

---

## 4. 他メンバーへのインターフェース

### A（サーバー・ゲーム進行）へ

- `BlockingQueue<Message>` からメッセージをデキューしてゲームロジックを実装する
- `tryAnswer(clientId)` の仕様は2-4を参照。不正解時は `answered = false` に戻してラウンドを継続する
- クライアントへの送信は `ClientHandler.send(type, body)` 経由で行う（メソッドはBが実装）

### C（クライアント・テスト）へ

- 接続先: `localhost:8080`、起動コマンド: `java apps.Client`
- 接続直後に `CONNECT`（ボディなし）を送ること。プレイヤー名はサーバーが採番する
- フレーミング仕様は2-1を参照。ボディのJSONはUTF-8
- 複数クライアント起動用のシェルスクリプトをCが整備予定
