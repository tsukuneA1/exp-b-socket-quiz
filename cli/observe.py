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


def ts() -> str:
    return datetime.now().strftime("%H:%M:%S.%f")[:-3]


def handle(event: dict) -> None:
    kind = event.get("event")

    if kind == "connected":
        console.print(f"[dim]{ts()}[/] [bold green]Observer connected to EventBus[/]")

    elif kind == "round_start":
        r = event["round"]
        total = event["total"]
        q = event["question"]
        console.rule(f"[bold cyan]Round {r} / {total}[/]")
        console.print(Panel(q, title="[yellow]Question[/]", border_style="yellow"))

    elif kind == "answer_correct":
        player = event["player"]
        ms = event["round_ms"]
        console.print(
            f"[dim]{ts()}[/] [bold green]CORRECT[/]  "
            f"[cyan]{player}[/]  [dim]{ms} ms[/]"
        )

    elif kind == "answer_wrong":
        player = event["player"]
        console.print(f"[dim]{ts()}[/] [red]WRONG[/]    [cyan]{player}[/]")

    elif kind == "answer_late":
        player = event["player"]
        delta = event["delta_us"]
        console.print(
            f"[dim]{ts()}[/] [dim]LATE[/]     [cyan]{player}[/]  "
            f"[dim]+{delta} µs after winner[/]"
        )

    elif kind == "answer_cas_rejected":
        player = event["player"]
        delta = event["delta_us"]
        console.print(
            f"[dim]{ts()}[/] [yellow]CAS-MISS[/] [cyan]{player}[/]  "
            f"[dim]+{delta} µs (lost race)[/]"
        )

    elif kind == "round_all_wrong":
        r = event["round"]
        ms = event["round_ms"]
        console.print(
            f"[dim]{ts()}[/] [bold red]ALL WRONG[/]  round {r}  [dim]{ms} ms[/]"
        )

    elif kind == "game_end":
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
