from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

RoutingIntent = Literal[
    "polish",
    "sentence_structure",
    "vocab",
    "translation",
    "scoring",
    "practice_design",
    "ability_profile",
    "learning_planner",
]


class HandoffRoutingMetadata(BaseModel):
    intent: RoutingIntent = Field(description="Normalized routing intent chosen by the router.")
    reason: str = Field(description="Brief reason for choosing this specialist.")
    confidence: float = Field(ge=0.0, le=1.0, description="Router confidence from 0 to 1.")
