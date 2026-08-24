import { z } from "zod";

export const TutorDecisionSchema = z.object({
  action: z.enum([
    "NEXT_EXERCISE",
    "RETRY",
    "EXPLAIN",
    "INSERT_PREREQUISITE",
    "SCHEDULE_REVIEW",
  ]),
  message: z.string(),
  targetConceptId: z.string().nullable().default(null),
  reason: z.string(),
});

export type TutorDecision = z.infer<typeof TutorDecisionSchema>;

export const TutorDecisionRequestSchema = z.object({
  learnerId: z.string().min(1),
  lessonId: z.string().min(1),
  recognizedText: z.string(),
  currentConceptId: z.string().min(1),
  gradingSummary: z.object({
    correct: z.boolean(),
    errors: z.array(z.string()).default([]),
    hintLevel: z.number().int().min(0).max(3),
  }),
});

export type TutorDecisionRequest = z.infer<typeof TutorDecisionRequestSchema>;
