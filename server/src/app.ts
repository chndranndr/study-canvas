import Fastify from "fastify";
import { registerHealthRoutes } from "./api/health.js";
import { registerInkRoutes } from "./api/ink.js";
import { registerLessonRoutes } from "./api/lessons.js";
import { registerTutorRoutes } from "./api/tutor.js";

export function buildApp() {
  const app = Fastify({ logger: true });

  void app.register(registerHealthRoutes);
  void app.register(registerTutorRoutes);
  void app.register(registerLessonRoutes);
  void app.register(registerInkRoutes);

  return app;
}
