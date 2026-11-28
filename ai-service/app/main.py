import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse

from app.config import settings
from app.copilot import Copilot, CopilotRequest
from app.events.consumer import run_consumer
from app.events.index import EventIndex
from app.explain import RiskExplainer, RiskExplainRequest, RiskExplanation
from app.llm import LlmClient
from app.recon import AnomalySummary, ReconAggregates, ReconSummarizer
from app.scoring.model import LogisticModel, ScoreRequest, ScoreResponse
from app.traces import TraceClient

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


async def _prune_forever(index: EventIndex, days: int) -> None:
    while True:
        index.prune(days)
        await asyncio.sleep(3600)


@asynccontextmanager
async def lifespan(app: FastAPI):
    llm = LlmClient(settings)
    index = EventIndex(settings.event_db)
    app.state.model = LogisticModel()
    app.state.llm = llm
    app.state.index = index
    app.state.explainer = RiskExplainer(llm)
    app.state.recon = ReconSummarizer(llm)
    app.state.copilot = Copilot(llm, index, TraceClient(settings.jaeger_url))
    tasks = [asyncio.create_task(_prune_forever(index, settings.event_retention_days))]
    if settings.kafka_enabled:
        tasks.append(asyncio.create_task(run_consumer(index, settings.kafka_bootstrap)))
    yield
    for task in tasks:
        task.cancel()
    await asyncio.gather(*tasks, return_exceptions=True)
    index.close()


app = FastAPI(title="FinCore ai-service", lifespan=lifespan)


@app.get("/health")
def health(request: Request) -> dict:
    s = request.app.state
    return {"status": "UP", "modelVersion": s.model.version, "llm": s.llm.name, "indexedEvents": s.index.count()}


@app.post("/v1/score", response_model=ScoreResponse)
def score(body: ScoreRequest, request: Request) -> ScoreResponse:
    return request.app.state.model.score(body)


@app.post("/v1/explain/risk", response_model=RiskExplanation)
async def explain_risk(body: RiskExplainRequest, request: Request) -> RiskExplanation:
    return await request.app.state.explainer.explain(body)


@app.post("/v1/recon/summarize", response_model=AnomalySummary)
async def summarize_recon(body: ReconAggregates, request: Request) -> AnomalySummary:
    return await request.app.state.recon.summarize(body)


@app.post("/v1/copilot/stream")
async def copilot(body: CopilotRequest, request: Request) -> StreamingResponse:
    return StreamingResponse(request.app.state.copilot.answer(body), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})