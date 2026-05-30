#!/usr/bin/env python3
"""Quiz game observer — connects to EventBus and displays live events."""

import json
import socket
import sys
from datetime import datetime

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.text import Text
from rich import box

HOST = "localhost"
PORT = 9090

console = Console()
current_round = 0
trace_rows: dict[str, dict] = {}


def ts() -> str:
    return datetime.now().strftime("%H:%M:%S.%f")[:-3]


def row_key(event: dict) -> str:
    return str(event.get("player_id", event.get("player", "")))


def get_row(event: dict) -> dict:
    key = row_key(event)
    row = trace_rows.setdefault(
        key,
        {
            "player": event.get("player", key),
            "answer": event.get("answer", ""),
            "recv_us": None,
            "enter_us": None,
            "accept_us": None,
            "result": "",
            "delta_us": 0,
        },
    )
    row["player"] = event.get("player", row["player"])
    row["answer"] = event.get("answer", row["answer"])
    return row


def fmt_us(value) -> str:
    return "" if value is None else f"{value:,}"


def fmt_result(row: dict) -> Text:
    result = row.get("result", "")
    delta = row.get("delta_us", 0)
    if result == "SUCCESS":
        return Text("SUCCESS", style="bold green")
    if result == "ACCEPT_FAIL":
        return Text(f"ACCEPT_FAIL +{delta}us", style="yellow")
    if result == "LATE":
        return Text(f"LATE +{delta}us", style="dim")
    if result == "WRONG":
        return Text("WRONG", style="red")
    return Text("")


def render_trace_table(reason: str = "") -> None:
    if not trace_rows:
        return

    table = Table(
        title=f"Round {current_round} / answer linearization trace{reason}",
        box=box.SIMPLE_HEAVY,
        show_lines=False,
    )
    table.add_column("player", style="cyan")
    table.add_column("answer", justify="right")
    table.add_column("recv +us", justify="right")
    table.add_column("enter +us", justify="right")
    table.add_column("accept +us", justify="right")
    table.add_column("result")

    rows = sorted(
        trace_rows.values(),
        key=lambda r: (
            r["recv_us"] is None,
            r["recv_us"] if r["recv_us"] is not None else 0,
            str(r["player"]),
        ),
    )
    for row in rows:
        table.add_row(
            str(row["player"]),
            str(row["answer"]),
            fmt_us(row["recv_us"]),
            fmt_us(row["enter_us"]),
            fmt_us(row["accept_us"]),
            fmt_result(row),
        )
    console.print(table)


def handle(event: dict) -> None:
    global current_round, trace_rows
    kind = event.get("event")

    if kind == "connected":
        console.print(f"[dim]{ts()}[/] [bold green]Observer connected to EventBus[/]")

    elif kind == "round_start":
        r = event["round"]
        total = event["total"]
        q = event["question"]
        render_trace_table(" / before next round")
        current_round = r
        trace_rows = {}
        console.rule(f"[bold cyan]Round {r} / {total}[/]")
        console.print(Panel(q, title="[yellow]Question[/]", border_style="yellow"))

    elif kind == "answer_trace":
        row = get_row(event)
        stage = event["stage"]
        if stage == "received":
            row["recv_us"] = event["offset_us"]
        elif stage == "enter":
            row["enter_us"] = event["offset_us"]
        elif stage == "accept_attempt":
            row["accept_us"] = event["offset_us"]

    elif kind == "answer_result":
        row = get_row(event)
        row["recv_us"] = event.get("recv_us")
        row["enter_us"] = event.get("enter_us")
        row["accept_us"] = event.get("accept_us")
        row["result"] = event["result"]
        row["delta_us"] = event.get("delta_us", 0)
        render_trace_table()

    elif kind == "queue_enqueue":
        console.print(
            f"[dim]{ts()}[/] [blue]ENQUEUE[/] "
            f"#{event['seq']} {event['kind']} "
            f"{event.get('player', '')}  [dim]size={event['size']}[/]"
        )

    elif kind == "queue_dequeue":
        console.print(
            f"[dim]{ts()}[/] [magenta]DEQUEUE[/] "
            f"#{event['seq']} {event['kind']} "
            f"{event.get('player', '')}  "
            f"[dim]wait={event['queue_wait_us']}us size={event['size']}[/]"
        )

    elif kind == "answer_correct":
        pass

    elif kind == "answer_wrong":
        pass

    elif kind == "answer_late":
        pass

    elif kind == "round_all_wrong":
        r = event["round"]
        ms = event["round_ms"]
        console.print(
            f"[dim]{ts()}[/] [bold red]ALL WRONG[/]  round {r}  [dim]{ms} ms[/]"
        )

    elif kind == "game_end":
        render_trace_table(" / before game end")
        winner = event.get("winner", "")
        if winner:
            console.rule()
            console.print(
                Panel(
                    f"[bold yellow]{winner}[/] wins!",
                    title="[bold magenta]GAME OVER[/]",
                    border_style="magenta",
                    box=box.DOUBLE,
                )
            )
        else:
            console.rule()
            console.print(
                Panel("Tie — no winner", title="[bold magenta]GAME OVER[/]", border_style="magenta")
            )

    else:
        console.print(f"[dim]{ts()}[/] [dim]{event}[/]")


def main() -> None:
    host = sys.argv[1] if len(sys.argv) > 1 else HOST
    port = int(sys.argv[2]) if len(sys.argv) > 2 else PORT

    console.print(f"[dim]Connecting to {host}:{port}...[/]")
    try:
        with socket.create_connection((host, port)) as sock:
            buf = ""
            while True:
                chunk = sock.recv(4096).decode("utf-8", errors="replace")
                if not chunk:
                    console.print("[red]Connection closed by server.[/]")
                    break
                buf += chunk
                while "\n" in buf:
                    line, buf = buf.split("\n", 1)
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        handle(json.loads(line))
                    except json.JSONDecodeError:
                        console.print(f"[dim]raw: {line}[/]")
    except ConnectionRefusedError:
        console.print(f"[red]Could not connect to {host}:{port}. Is the server running?[/]")
        sys.exit(1)
    except KeyboardInterrupt:
        console.print("\n[dim]Observer disconnected.[/]")


if __name__ == "__main__":
    main()
