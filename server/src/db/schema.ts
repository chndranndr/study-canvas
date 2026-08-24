import { integer, real, sqliteTable, text, uniqueIndex } from "drizzle-orm/sqlite-core";

const timestamps = {
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp_ms" }).notNull(),
};

export const learnerProfiles = sqliteTable("learner_profiles", {
  id: text("id").primaryKey(),
  displayName: text("display_name").notNull(),
  nativeLanguage: text("native_language").notNull().default("id"),
  targetLevel: text("target_level"),
  learningGoal: text("learning_goal"),
  teachingPreferencesJson: text("teaching_preferences_json").notNull().default("{}"),
  ...timestamps,
});

export const knowledgeNodes = sqliteTable("knowledge_nodes", {
  id: text("id").primaryKey(),
  kind: text("kind", { enum: ["kana", "vocabulary", "grammar", "kanji", "production"] }).notNull(),
  name: text("name").notNull(),
  jlptLevel: text("jlpt_level"),
  difficulty: integer("difficulty").notNull().default(1),
  referenceJson: text("reference_json").notNull().default("{}"),
});

export const knowledgeEdges = sqliteTable(
  "knowledge_edges",
  {
    id: text("id").primaryKey(),
    fromNodeId: text("from_node_id").notNull().references(() => knowledgeNodes.id),
    toNodeId: text("to_node_id").notNull().references(() => knowledgeNodes.id),
    relation: text("relation", { enum: ["prerequisite", "related", "confusable_with"] }).notNull(),
  },
  (table) => [uniqueIndex("knowledge_edge_unique").on(table.fromNodeId, table.toNodeId, table.relation)],
);

export const learnerMastery = sqliteTable(
  "learner_mastery",
  {
    id: text("id").primaryKey(),
    learnerId: text("learner_id").notNull().references(() => learnerProfiles.id),
    nodeId: text("node_id").notNull().references(() => knowledgeNodes.id),
    score: real("score").notNull().default(0),
    confidence: real("confidence").notNull().default(0),
    attempts: integer("attempts").notNull().default(0),
    hintRate: real("hint_rate").notNull().default(0),
    lastPracticedAt: integer("last_practiced_at", { mode: "timestamp_ms" }),
    ...timestamps,
  },
  (table) => [uniqueIndex("learner_mastery_unique").on(table.learnerId, table.nodeId)],
);

export const lessons = sqliteTable("lessons", {
  id: text("id").primaryKey(),
  learnerId: text("learner_id").notNull().references(() => learnerProfiles.id),
  title: text("title").notNull(),
  level: text("level"),
  status: text("status", { enum: ["planned", "active", "mastered", "archived"] }).notNull().default("planned"),
  worldWidth: real("world_width").notNull().default(2400),
  worldHeight: real("world_height").notNull().default(3200),
  ...timestamps,
});

export const lessonElements = sqliteTable("lesson_elements", {
  id: text("id").primaryKey(),
  lessonId: text("lesson_id").notNull().references(() => lessons.id, { onDelete: "cascade" }),
  kind: text("kind", { enum: ["lesson_text", "example", "exercise", "ink", "ai_annotation", "user_text"] }).notNull(),
  x: real("x").notNull(),
  y: real("y").notNull(),
  width: real("width").notNull(),
  height: real("height").notNull(),
  zIndex: integer("z_index").notNull().default(0),
  readOnly: integer("read_only", { mode: "boolean" }).notNull().default(false),
  movable: integer("movable", { mode: "boolean" }).notNull().default(true),
  payloadJson: text("payload_json").notNull().default("{}"),
  ...timestamps,
});

export const exerciseAttempts = sqliteTable("exercise_attempts", {
  id: text("id").primaryKey(),
  learnerId: text("learner_id").notNull().references(() => learnerProfiles.id),
  lessonId: text("lesson_id").notNull().references(() => lessons.id),
  exerciseElementId: text("exercise_element_id").notNull().references(() => lessonElements.id),
  recognizedText: text("recognized_text").notNull(),
  correct: integer("correct", { mode: "boolean" }).notNull(),
  grammarScore: real("grammar_score"),
  meaningScore: real("meaning_score"),
  naturalnessScore: real("naturalness_score"),
  hintLevel: integer("hint_level").notNull().default(0),
  responseTimeMs: integer("response_time_ms"),
  errorsJson: text("errors_json").notNull().default("[]"),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull(),
});

export const tutorMemories = sqliteTable("tutor_memories", {
  id: text("id").primaryKey(),
  learnerId: text("learner_id").notNull().references(() => learnerProfiles.id),
  category: text("category", { enum: ["preference", "mistake_pattern", "teaching_policy", "goal"] }).notNull(),
  content: text("content").notNull(),
  confidence: real("confidence").notNull().default(0.5),
  ...timestamps,
});

export const reviewSchedule = sqliteTable("review_schedule", {
  id: text("id").primaryKey(),
  learnerId: text("learner_id").notNull().references(() => learnerProfiles.id),
  nodeId: text("node_id").notNull().references(() => knowledgeNodes.id),
  dueAt: integer("due_at", { mode: "timestamp_ms" }).notNull(),
  stability: real("stability"),
  difficulty: real("difficulty"),
  ...timestamps,
});
