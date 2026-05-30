#!/usr/bin/env python3
"""Race simulator — connects N bots and fires ANSWER simultaneously.

Usage:
  python cli/race.py [--host HOST] [--port PORT] [--bots N] [--answer INDEX]

Example:
  python cli/race.py --bots 2 --answer 0

Run observe.py in a separate terminal to watch the linearization trace.
"""

import argparse
import socket
import struct
import threading
import time

# C → S
CONNECT = 0x01
ANSWER = 0x02
READY = 0x05

# S → C
CONNECT_ACK = 0x11
CONNECT_NG = 0x18
QUESTION_OPTIONS = 0x12
QUESTION_CHUNK = 0x17
WRONG_ANSWER = 0x13
ROUND_END = 0x14
SCORE = 0x15
GAME_END = 0x19
LOBBY_STATUS = 0x1A
DISCONNECT_ACK = 0x16


def send_frame(sock: socket.socket, msg_type: int, body: bytes = b"") -> None:
    sock.sendall(struct.pack(">BI", msg_type, len(body)) + body)


def recv_exactly(sock: socket.socket, n: int) -> bytes:
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("server closed connection")
        buf += chunk
    return buf


def read_frame(sock: socket.socket) -> tuple[int, bytes]:
    header = recv_exactly(sock, 5)
    msg_type = header[0]
    body_len = struct.unpack(">I", header[1:])[0]
    body = recv_exactly(sock, body_len)
    return msg_type, body


class BotResult:
    def __init__(self) -> None:
        self.player_id: int = -1
        self.won: bool = False
        self.error: str | None = None


def bot_thread(
    name: str,
    host: str,
    port: int,
    answer_index: int,
    barrier: threading.Barrier,
    result: BotResult,
) -> None:
    try:
        with socket.create_connection((host, port)) as sock:
            # CONNECT
            send_frame(sock, CONNECT, name.encode())
            print(f"[{name}] CONNECT sent")

            # wait for CONNECT_ACK (or CONNECT_NG)
            while True:
                t, b = read_frame(sock)
                if t == CONNECT_ACK:
                    result.player_id = b[0]
                    print(f"[{name}] CONNECT_ACK  id={result.player_id}")
                    break
                if t == CONNECT_NG:
                    reason = b.decode(errors="replace")
                    result.error = f"CONNECT_NG: {reason}"
                    print(f"[{name}] CONNECT_NG: {reason}")
                    barrier.abort()
                    return

            # READY
            send_frame(sock, READY)
            print(f"[{name}] READY sent")

            # wait for QUESTION_OPTIONS (game started, round 1 beginning)
            print(f"[{name}] waiting for QUESTION_OPTIONS...")
            while True:
                t, b = read_frame(sock)
                if t == QUESTION_OPTIONS:
                    print(f"[{name}] QUESTION_OPTIONS received — at barrier")
                    break
                # ignore LOBBY_STATUS while waiting

            # synchronize all bots, then fire ANSWER at the same instant
            barrier.wait()
            send_frame(sock, ANSWER, bytes([answer_index]))
            sent_at = time.monotonic_ns()
            print(f"[{name}] ANSWER({answer_index}) sent at {sent_at}")

            # wait for ROUND_END
            while True:
                t, b = read_frame(sock)
                if t == ROUND_END:
                    winner_id = b[0]
                    result.won = winner_id == result.player_id
                    label = "WIN" if result.won else "lose"
                    print(f"[{name}] ROUND_END  winner_id={winner_id}  → {label}")
                    break
                if t == WRONG_ANSWER:
                    print(f"[{name}] WRONG_ANSWER")
                if t == GAME_END:
                    winner_id = b[0]
                    result.won = winner_id == result.player_id
                    print(f"[{name}] GAME_END  winner_id={winner_id}")
                    break

    except threading.BrokenBarrierError:
        result.error = "barrier aborted (another bot failed)"
    except Exception as e:
        result.error = str(e)
        print(f"[{name}] ERROR: {e}")
        try:
            barrier.abort()
        except Exception:
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Race simulator for socket-quiz")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--bots", type=int, default=2, help="number of bots (default: 2)")
    parser.add_argument("--answer", type=int, default=0, help="answer index to send (default: 0)")
    args = parser.parse_args()

    print(f"Starting {args.bots} bots → {args.host}:{args.port}  answer={args.answer}")
    print("(Run 'python cli/observe.py' in another terminal to watch the trace)\n")

    barrier = threading.Barrier(args.bots)
    results = [BotResult() for _ in range(args.bots)]
    threads = [
        threading.Thread(
            target=bot_thread,
            args=(f"bot{i+1}", args.host, args.port, args.answer, barrier, results[i]),
            daemon=True,
        )
        for i in range(args.bots)
    ]

    for t in threads:
        t.start()
    for t in threads:
        t.join()

    print("\n── result ──────────────────────")
    for i, r in enumerate(results):
        if r.error:
            print(f"  bot{i+1}: ERROR — {r.error}")
        else:
            print(f"  bot{i+1} (id={r.player_id}): {'WIN' if r.won else 'lose'}")


if __name__ == "__main__":
    main()
