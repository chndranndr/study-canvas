import { createAgentSession, SessionManager } from "@earendil-works/pi-coding-agent";
import { TutorDecisionSchema, type TutorDecision, type TutorDecisionRequest } from "../learning/tutor-decision.js";

function extractJson(text: string): unknown {
  const trimmed = text.trim();
  const unfenced = trimmed
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "");
  return JSON.parse(unfenced);
}

export async function decideTutorAction(request: TutorDecisionRequest): Promise<TutorDecision> {
  const { session } = await createAgentSession({
    sessionManager: SessionManager.inMemory(),
    tools: [],
  });

  let output = "";
  session.subscribe((event) => {
    if (event.type === "message_update" && event.assistantMessageEvent.type === "text_delta") {
      output += event.assistantMessageEvent.delta;
    }
  });

  await session.prompt(`
You are the Study Canvas Japanese tutor.
Follow the project Japanese learning skill and choose exactly one pedagogical next action.
Do not invent learner facts that are not present below.
Return ONLY valid JSON with this schema:
{
  "action": "NEXT_EXERCISE|RETRY|EXPLAIN|INSERT_PREREQUISITE|SCHEDULE_REVIEW",
  "message": "short learner-facing feedback",
  "targetConceptId": "concept-id or null",
  "reason": "brief internal rationale"
}

Attempt context:
${JSON.stringify(request)}
`);

  return TutorDecisionSchema.parse(extractJson(output));
}
