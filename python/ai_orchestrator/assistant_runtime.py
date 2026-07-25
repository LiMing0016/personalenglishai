from __future__ import annotations

from .schemas.assistant_request import AssistantRequest
from .services.assistant_runtime_mode import AssistantRuntimeModeResolver


class AssistantRuntime:
    def __init__(
        self,
        *,
        multi_agent_service,
        raw_service,
        mode_resolver: AssistantRuntimeModeResolver,
    ) -> None:
        self.multi_agent_service = multi_agent_service
        self.raw_service = raw_service
        self.mode_resolver = mode_resolver

    @classmethod
    def from_env(cls) -> "AssistantRuntime":
        from .assistant_service import AssistantAgentService
        from .raw_assistant_service import RawSingleAgentService

        return cls(
            multi_agent_service=AssistantAgentService.from_env(),
            raw_service=RawSingleAgentService.from_env(),
            mode_resolver=AssistantRuntimeModeResolver.from_env(),
        )

    @property
    def model(self) -> str:
        return self.multi_agent_service.model

    def is_configured(self) -> bool:
        return self.multi_agent_service.is_configured()

    def _service_for(self, request: AssistantRequest):
        mode = self.mode_resolver.resolve(request.agent_mode)
        if mode == "single_agent_raw":
            return self.raw_service
        return self.multi_agent_service

    async def run_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ):
        service = self._service_for(request)
        return await service.run_assistant_request(request, authorization=authorization)

    async def stream_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ):
        service = self._service_for(request)
        async for event in service.stream_assistant_request(request, authorization=authorization):
            yield event

    async def route_assistant_request(
        self,
        request: AssistantRequest,
        authorization: str | None = None,
    ):
        return await self.multi_agent_service.route_assistant_request(
            request,
            authorization=authorization,
        )

    async def chat(self, **kwargs):
        return await self.multi_agent_service.chat(**kwargs)
