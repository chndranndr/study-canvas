import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { sqlite } from "../db/client.js";

const paramsSchema = z.object({
  id: z.string().min(1).max(128),
  exerciseElementId: z.string().min(1).max(128),
});

const deleteParamsSchema = paramsSchema.extend({
  strokeId: z.string().min(1).max(128),
});

const optionalUnitValue = z.number().finite().min(0).max(1).nullable();
const optionalAngle = z.number().finite().min(-7).max(7).nullable();

const pointSchema = z.object({
  x: z.number().finite().min(-20_000).max(20_000),
  y: z.number().finite().min(-20_000).max(20_000),
  elapsedTimeMs: z.number().int().min(0).max(86_400_000),
  pressure: optionalUnitValue.optional().default(null),
  tiltRadians: optionalAngle.optional().default(null),
  orientationRadians: optionalAngle.optional().default(null),
});

const strokeSchema = z.object({
  id: z.string().min(1).max(128),
  sequence: z.number().int().min(0).max(100_000),
  toolType: z.string().min(1).max(32).default("stylus"),
  brush: z.object({
    family: z.string().min(1).max(64),
    colorArgb: z.number().int(),
    size: z.number().finite().positive().max(100),
    epsilon: z.number().finite().positive().max(10),
  }),
  points: z.array(pointSchema).min(1).max(20_000),
});

type RawInkStroke = {
  id: string;
  sequence: number;
  tool_type: string;
  brush_json: string;
  points_json: string;
};

function ensureInkTable() {
  sqlite.exec(`
    CREATE TABLE IF NOT EXISTS ink_strokes (
      id TEXT PRIMARY KEY NOT NULL,
      lesson_id TEXT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
      exercise_element_id TEXT NOT NULL REFERENCES lesson_elements(id) ON DELETE CASCADE,
      sequence INTEGER NOT NULL,
      tool_type TEXT NOT NULL DEFAULT 'stylus',
      brush_json TEXT NOT NULL,
      points_json TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );
    CREATE UNIQUE INDEX IF NOT EXISTS ink_stroke_exercise_sequence_unique
      ON ink_strokes(exercise_element_id, sequence);
  `);
}

function exerciseExists(lessonId: string, exerciseElementId: string): boolean {
  return Boolean(
    sqlite.prepare(`
      SELECT 1
      FROM lesson_elements
      WHERE id = ? AND lesson_id = ? AND kind = 'exercise'
    `).get(exerciseElementId, lessonId),
  );
}

function mapStroke(row: RawInkStroke) {
  return {
    id: row.id,
    sequence: row.sequence,
    toolType: row.tool_type,
    brush: JSON.parse(row.brush_json) as unknown,
    points: JSON.parse(row.points_json) as unknown,
  };
}

export async function registerInkRoutes(app: FastifyInstance) {
  ensureInkTable();

  app.get(
    "/api/lessons/:id/exercises/:exerciseElementId/ink",
    async (request, reply) => {
      const params = paramsSchema.safeParse(request.params);
      if (!params.success) return reply.code(400).send({ error: "invalid_ink_params" });

      const { id: lessonId, exerciseElementId } = params.data;
      if (!exerciseExists(lessonId, exerciseElementId)) {
        return reply.code(404).send({ error: "exercise_not_found" });
      }

      const rows = sqlite.prepare(`
        SELECT id, sequence, tool_type, brush_json, points_json
        FROM ink_strokes
        WHERE lesson_id = ? AND exercise_element_id = ?
        ORDER BY sequence ASC, created_at ASC
      `).all(lessonId, exerciseElementId) as RawInkStroke[];

      return rows.map(mapStroke);
    },
  );

  app.post(
    "/api/lessons/:id/exercises/:exerciseElementId/ink",
    async (request, reply) => {
      const params = paramsSchema.safeParse(request.params);
      const body = strokeSchema.safeParse(request.body);
      if (!params.success || !body.success) {
        return reply.code(400).send({ error: "invalid_ink_stroke" });
      }

      const { id: lessonId, exerciseElementId } = params.data;
      if (!exerciseExists(lessonId, exerciseElementId)) {
        return reply.code(404).send({ error: "exercise_not_found" });
      }

      const timestamp = Date.now();
      const stroke = body.data;
      sqlite.prepare(`
        INSERT INTO ink_strokes (
          id, lesson_id, exercise_element_id, sequence, tool_type,
          brush_json, points_json, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          sequence = excluded.sequence,
          tool_type = excluded.tool_type,
          brush_json = excluded.brush_json,
          points_json = excluded.points_json,
          updated_at = excluded.updated_at
      `).run(
        stroke.id,
        lessonId,
        exerciseElementId,
        stroke.sequence,
        stroke.toolType,
        JSON.stringify(stroke.brush),
        JSON.stringify(stroke.points),
        timestamp,
        timestamp,
      );

      return reply.code(201).send({ saved: true, strokeId: stroke.id });
    },
  );

  app.delete(
    "/api/lessons/:id/exercises/:exerciseElementId/ink/:strokeId",
    async (request, reply) => {
      const params = deleteParamsSchema.safeParse(request.params);
      if (!params.success) return reply.code(400).send({ error: "invalid_ink_params" });

      const { id: lessonId, exerciseElementId, strokeId } = params.data;
      const result = sqlite.prepare(`
        DELETE FROM ink_strokes
        WHERE id = ? AND lesson_id = ? AND exercise_element_id = ?
      `).run(strokeId, lessonId, exerciseElementId);

      if (result.changes === 0) {
        return reply.code(404).send({ error: "stroke_not_found" });
      }
      return { deleted: true, strokeId };
    },
  );
}
