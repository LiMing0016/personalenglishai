import os
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from python.ai_orchestrator.services.raw_fetch_mcp import (
    RawFetchMcpConfig,
    connected_raw_fetch_mcp_servers,
    create_raw_fetch_mcp_server,
)


class RawFetchMcpConfigTest(unittest.TestCase):
    def test_from_env_defaults_to_disabled_when_variable_is_unset(self) -> None:
        with patch.dict(os.environ, {}, clear=True):
            config = RawFetchMcpConfig.from_env()

        self.assertFalse(config.enabled)
        self.assertEqual(config.package_version, "2026.7.10")

    def test_from_env_enables_fetch_for_true_value(self) -> None:
        with patch.dict(os.environ, {"AI_ASSISTANT_RAW_FETCH_MCP_ENABLED": " true "}, clear=True):
            config = RawFetchMcpConfig.from_env()

        self.assertTrue(config.enabled)


class CreateRawFetchMcpServerTest(unittest.TestCase):
    def test_creates_fetch_server_with_fixed_uvx_configuration(self) -> None:
        server = object()
        config = RawFetchMcpConfig(enabled=True)

        with patch("agents.mcp.MCPServerStdio", return_value=server) as server_class:
            result = create_raw_fetch_mcp_server(config)

        self.assertIs(result, server)
        server_class.assert_called_once_with(
            name="Fetch MCP",
            params={
                "command": "uvx",
                "args": [
                    "--from",
                    "mcp-server-fetch==2026.7.10",
                    "mcp-server-fetch",
                ],
                "env": {"PYTHONIOENCODING": "utf-8"},
            },
            cache_tools_list=True,
            client_session_timeout_seconds=30,
        )


class ConnectedRawFetchMcpServersTest(unittest.IsolatedAsyncioTestCase):
    async def test_disabled_does_not_create_a_server(self) -> None:
        config = RawFetchMcpConfig(enabled=False)

        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp.create_raw_fetch_mcp_server"
        ) as create_server:
            async with connected_raw_fetch_mcp_servers(config) as servers:
                self.assertEqual(servers, ())

        create_server.assert_not_called()

    async def test_connected_server_is_yielded_and_cleaned_up(self) -> None:
        server = MagicMock()
        server.connect = AsyncMock()
        server.cleanup = AsyncMock()

        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp.create_raw_fetch_mcp_server",
            return_value=server,
        ):
            async with connected_raw_fetch_mcp_servers(RawFetchMcpConfig(enabled=True)) as servers:
                self.assertEqual(servers, (server,))

        server.connect.assert_awaited_once_with()
        server.cleanup.assert_awaited_once_with()

    async def test_connect_failure_yields_empty_tuple_without_cleanup(self) -> None:
        server = MagicMock()
        server.connect = AsyncMock(side_effect=RuntimeError("cannot connect"))
        server.cleanup = AsyncMock()

        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp.create_raw_fetch_mcp_server",
            return_value=server,
        ):
            async with connected_raw_fetch_mcp_servers(RawFetchMcpConfig(enabled=True)) as servers:
                self.assertEqual(servers, ())

        server.cleanup.assert_not_awaited()

    async def test_caller_exception_is_reraised_after_cleanup(self) -> None:
        server = MagicMock()
        server.connect = AsyncMock()
        server.cleanup = AsyncMock()

        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp.create_raw_fetch_mcp_server",
            return_value=server,
        ):
            with self.assertRaisesRegex(ValueError, "caller failed"):
                async with connected_raw_fetch_mcp_servers(RawFetchMcpConfig(enabled=True)):
                    raise ValueError("caller failed")

        server.cleanup.assert_awaited_once_with()

    async def test_cleanup_failure_does_not_fail_normal_caller(self) -> None:
        server = MagicMock()
        server.connect = AsyncMock()
        server.cleanup = AsyncMock(side_effect=RuntimeError("cleanup failed"))

        with patch(
            "python.ai_orchestrator.services.raw_fetch_mcp.create_raw_fetch_mcp_server",
            return_value=server,
        ):
            async with connected_raw_fetch_mcp_servers(RawFetchMcpConfig(enabled=True)) as servers:
                self.assertEqual(servers, (server,))

        server.cleanup.assert_awaited_once_with()


if __name__ == "__main__":
    unittest.main()
