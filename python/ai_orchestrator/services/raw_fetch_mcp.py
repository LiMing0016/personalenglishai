import logging
import os
from contextlib import asynccontextmanager
from dataclasses import dataclass


log = logging.getLogger("uvicorn.error")


@dataclass(frozen=True)
class RawFetchMcpConfig:
    enabled: bool
    package_version: str = "2026.7.10"

    @classmethod
    def from_env(cls) -> "RawFetchMcpConfig":
        value = os.getenv("AI_ASSISTANT_RAW_FETCH_MCP_ENABLED", "")
        return cls(enabled=value.strip().lower() in {"1", "true", "yes", "on"})


def create_raw_fetch_mcp_server(config: RawFetchMcpConfig):
    from agents.mcp import MCPServerStdio

    return MCPServerStdio(
        name="Fetch MCP",
        params={
            "command": "uvx",
            "args": [
                "--from",
                f"mcp-server-fetch=={config.package_version}",
                "mcp-server-fetch",
            ],
            "env": {"PYTHONIOENCODING": "utf-8"},
        },
        cache_tools_list=True,
        client_session_timeout_seconds=30,
    )


@asynccontextmanager
async def connected_raw_fetch_mcp_servers(config: RawFetchMcpConfig):
    if not config.enabled:
        yield ()
        return

    server = None
    connected = False
    try:
        server = create_raw_fetch_mcp_server(config)
        await server.connect()
        connected = True
        await server.list_tools()
    except Exception:
        log.warning("Unable to initialize Fetch MCP; continuing without it", exc_info=True)
        if connected:
            try:
                await server.cleanup()
            except Exception:
                log.warning("Unable to clean up Fetch MCP", exc_info=True)
        yield ()
        return

    try:
        yield (server,)
    finally:
        try:
            await server.cleanup()
        except Exception:
            log.warning("Unable to clean up Fetch MCP", exc_info=True)
