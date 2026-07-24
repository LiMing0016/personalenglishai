from __future__ import annotations


def create_raw_single_agent(model: str):
    from agents import Agent

    return Agent(name="Raw Single Agent", model=model)
