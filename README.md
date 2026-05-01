# expb-realtime-quiz

Java Socket を用いたリアルタイム早押しクイズシステムです。

大学のソフトウェア制作課題として、TCP/IP 通信・ソケットプログラミング・独自プロトコル設計の理解を目的に制作しています。

## 概要

複数のクライアントがサーバに接続し、早押しクイズを行うサーバ・クライアント型アプリケーションです。

サーバは問題配信、早押し判定、回答権管理、スコア管理を行います。

```text
Client A ── TCP Socket ──┐
Client B ── TCP Socket ──┼── Server
Client C ── TCP Socket ──┘