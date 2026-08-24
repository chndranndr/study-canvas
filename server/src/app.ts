import Fastify from "fastify";
import { registerHealthRoutes } from "./api/health.js";
import { registerTutorRoutes } from "./api/tutor.js";

export function buildApp() {
  const app = Fastify({ logger: true });

  void app.register(registerHealthRoutes);
  void app.register(registerTutorRoutes);

  return app;
}
