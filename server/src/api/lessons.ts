import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { sqlite } from "../db/client.js";

const demoLearnerId = "local-dev";
const demoLessonId = "tai-desu-demo";

const lessonParamsSchema = z.object({
  id: z.string().min(1).max(128),
});

const coordinateSchema = z.number().finite().min(-20_000).max(20_000);
const layoutBodySchema = z.object({
  elements: z.array(
    z.object({
      id: z.string().min(1).max(128),
      x: coordinateSchema,
      y: coordinateSchema,
    }),
  ).min(1).max(100),
});

type RawLesson = {
  id: string;
  title: string;
  world_width: number;
  world_height: number;
};

type RawElement = {
  id: string;
  kind: string;
  x: number;
  y: number;
  width: number;
  height: number;
  z_index: number;
  read_only: number;
  movable: number;
  payload_json: string;
};

function now() {
  return Date.now();
}

function ensureDemoLesson() {
  const timestamp = now();
  const seed = sqlite.transaction(() => {
    sqlite.prepare(`
      INSERT INTO learner_profiles (
        id, display_name, native_language, target_level, learning_goal,
        teaching_preferences_json, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO NOTHING
    `).run(
      demoLearnerId,
      "Local learner",
      "id",
      "N4",
      "Build active Japanese production skill",
      "{}",
      timestamp,
      timestamp,
    );

    sqlite.prepare(`
      INSERT INTO lessons (
        id, learner_id, title, level, status, world_width, world_height, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO NOTHING
    `).run(
      demoLessonId,
      demoLearnerId,
      "～たいです",
      "N5",
      "active",
      2400,
      3200,
      timestamp,
      timestamp,
    );

    const insertElement = sqlite.prepare(`
      INSERT INTO lesson_elements (
        id, lesson_id, kind, x, y, width, height, z_index,
        read_only, movable, payload_json, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO NOTHING
    `);

    insertElement.run(
      "tai-desu-material",
      demoLessonId,
      "lesson_text",
      180,
      150,
      820,
      420,
      10,
      1,
      1,
      JSON.stringify({
        title: "～たいです",
        body:
          "Dipakai untuk menyatakan keinginan melakukan suatu tindakan.\n\n" +
          "Vます → buang ます → Vたいです\n\n" +
          "たべます → たべたいです\n" +
          "いきます → いきたいです",
      }),
      timestamp,
      timestamp,
    );

    insertElement.run(
      "tai-desu-exercise-1",
      demoLessonId,
      "exercise",
      220,
      760,
      900,
      480,
      5,
      1,
      0,
      JSON.stringify({
        title: "Latihan 1",
        prompt: "Saya ingin pergi ke Jepang.",
      }),
      timestamp,
      timestamp,
    );
  });

  seed();
}

function parsePayload(payloadJson: string): unknown {
  try {
    return JSON.parse(payloadJson) as unknown;
  } catch {
    return {};
  }
}

function readLesson(lessonId: string) {
  const lesson = sqlite.prepare(`
    SELECT id, title, world_width, world_height
    FROM lessons
    WHERE id = ?
  `).get(lessonId) as RawLesson | undefined;

  if (!lesson) return null;

  const elements = sqlite.prepare(`
    SELECT id, kind, x, y, width, height, z_index, read_only, movable, payload_json
    FROM lesson_elements
    WHERE lesson_id = ?
    ORDER BY z_index ASC, created_at ASC
  `).all(lessonId) as RawElement[];

  return {
    id: lesson.id,
    title: lesson.title,
    world: {
      width: lesson.world_width,
      height: lesson.world_height,
    },
    elements: elements.map((element) => ({
      id: element.id,
      kind: element.kind,
      x: element.x,
      y: element.y,
      width: element.width,
      height: element.height,
      zIndex: element.z_index,
      readOnly: element.read_only === 1,
      movable: element.movable === 1,
      payload: parsePayload(element.payload_json),
    })),
  };
}

export async function registerLessonRoutes(app: FastifyInstance) {
  app.get("/api/lessons/:id", async (request, reply) => {
    const parsedParams = lessonParamsSchema.safeParse(request.params);
    if (!parsedParams.success) {
      return reply.code(400).send({ error: "invalid_lesson_id" });
    }

    if (parsedParams.data.id === demoLessonId) {
      ensureDemoLesson();
    }

    const lesson = readLesson(parsedParams.data.id);
    if (!lesson) {
      return reply.code(404).send({ error: "lesson_not_found" });
    }

    return lesson;
  });

  app.post("/api/lessons/:id/elements/layout", async (request, reply) => {
    const parsedParams = lessonParamsSchema.safeParse(request.params);
    const parsedBody = layoutBodySchema.safeParse(request.body);
    if (!parsedParams.success || !parsedBody.success) {
      return reply.code(400).send({ error: "invalid_layout_request" });
    }

    const lessonId = parsedParams.data.id;
    if (lessonId === demoLessonId) {
      ensureDemoLesson();
    }

    const lessonExists = sqlite.prepare("SELECT 1 FROM lessons WHERE id = ?").get(lessonId);
    if (!lessonExists) {
      return reply.code(404).send({ error: "lesson_not_found" });
    }

    const update = sqlite.prepare(`
      UPDATE lesson_elements
      SET x = ?, y = ?, updated_at = ?
      WHERE id = ? AND lesson_id = ? AND movable = 1
    `);

    const save = sqlite.transaction(() => {
      let updated = 0;
      const timestamp = now();
      for (const element of parsedBody.data.elements) {
        const result = update.run(element.x, element.y, timestamp, element.id, lessonId);
        updated += result.changes;
      }
      return updated;
    });

    const updated = save();
    return {
      updated,
      lesson: readLesson(lessonId),
    };
  });
}
