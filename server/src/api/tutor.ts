import type { FastifyInstance } from "fastify";
import { decideTutorAction } from "../agent/pi-tutor.js";
import { TutorDecisionRequestSchema } from "../learning/tutor-decision.js";

export async function registerTutorRoutes(app: FastifyInstance) {
  app.post("/api/tutor/decision", async (request, reply) => {
    const parsed = TutorDecisionRequestSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({
        error: "invalid_request",
        issues: parsed.error.issues,
      });
    }

    try {
      const decision = await decideTutorAction(parsed.data);
      return { decision };
    } catch (error) {
      request.log.error(error);
      return reply.code(502).send({
        error: "tutor_unavailable",
        message: "Pi tutor could not produce a valid decision. Check Pi authentication/model configuration.",
      });
    }
  });
}
