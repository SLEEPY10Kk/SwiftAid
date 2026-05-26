from __future__ import annotations

from fastapi import WebSocket


class ConfigBroadcaster:
    """Tracks mobile WebSocket clients that want live threshold updates."""

    def __init__(self) -> None:
        self._clients: set[WebSocket] = set()

    async def connect(self, websocket: WebSocket) -> None:
        await websocket.accept()
        self._clients.add(websocket)

    def disconnect(self, websocket: WebSocket) -> None:
        self._clients.discard(websocket)

    async def broadcast(self, payload: dict) -> None:
        stale_clients = []
        for websocket in self._clients:
            try:
                await websocket.send_json(payload)
            except RuntimeError:
                stale_clients.append(websocket)

        for websocket in stale_clients:
            self.disconnect(websocket)
