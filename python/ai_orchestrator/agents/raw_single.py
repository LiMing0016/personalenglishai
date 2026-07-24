from __future__ import annotations


def create_raw_single_agent(model: str, *, mcp_servers=()):
    from agents import Agent, WebSearchTool

    return Agent(
        name="Raw Single Agent",
        model=model,
        tools=[WebSearchTool()],
        mcp_servers=list(mcp_servers),
    )
