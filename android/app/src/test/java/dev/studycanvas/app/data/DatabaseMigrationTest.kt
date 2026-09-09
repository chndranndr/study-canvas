package dev.studycanvas.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates Room database migrations from v1 to v3.
 * Verifies version numbers, execution order, and exact DDL schema contracts
 * on pure JVM SupportSQLiteDatabase, with optional real SQLite CLI verification when available.
 */
class DatabaseMigrationTest {

    private fun createRecordingDatabase(recordedSql: MutableList<String>): SupportSQLiteDatabase {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    recordedSql.add((args[0] as String).trim())
                    null
                }
                "isOpen" -> true
                "isReadOnly" -> false
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler,
        ) as SupportSQLiteDatabase
    }

    @Test
    fun migrations_haveCorrectVersionsAndExecuteExpectedDDL() {
        // 1. Verify Migration 1 -> 2
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)

        val recorded12 = mutableListOf<String>()
        val db12 = createRecordingDatabase(recorded12)
        AppDatabase.MIGRATION_1_2.migrate(db12)
        assertEquals(1, recorded12.size)
        val sql12 = recorded12.first()
        assertTrue("Must create generated_lessons table", sql12.contains("CREATE TABLE IF NOT EXISTS `generated_lessons`"))
        assertTrue("Must include grammarId primary key", sql12.contains("`grammarId` TEXT NOT NULL"))
        assertTrue("Must include generatorVersion", sql12.contains("`generatorVersion` TEXT NOT NULL"))
        assertTrue("Must include summary", sql12.contains("`summary` TEXT NOT NULL"))
        assertTrue("Must include formation", sql12.contains("`formation` TEXT NOT NULL"))
        assertTrue("Must include commonMistakesJson", sql12.contains("`commonMistakesJson` TEXT NOT NULL"))
        assertTrue("Must include notesJson", sql12.contains("`notesJson` TEXT NOT NULL"))
        assertTrue("Must include exercisesJson", sql12.contains("`exercisesJson` TEXT NOT NULL"))
        assertTrue("Must include generatedAt", sql12.contains("`generatedAt` INTEGER NOT NULL"))
        assertTrue("Must declare primary key", sql12.contains("PRIMARY KEY(`grammarId`)"))

        // 2. Verify Migration 2 -> 3
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)

        val recorded23 = mutableListOf<String>()
        val db23 = createRecordingDatabase(recorded23)
        AppDatabase.MIGRATION_2_3.migrate(db23)
        assertEquals(1, recorded23.size)
        val sql23 = recorded23.first()
        assertEquals("ALTER TABLE `exercise_attempts` ADD COLUMN `matchedAcceptedAnswer` TEXT", sql23)

        // 3. Verify Migration 1 -> 3 executes both in sequence
        assertEquals(1, AppDatabase.MIGRATION_1_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_1_3.endVersion)

        val recorded13 = mutableListOf<String>()
        val db13 = createRecordingDatabase(recorded13)
        AppDatabase.MIGRATION_1_3.migrate(db13)
        assertEquals(2, recorded13.size)
        assertEquals(sql12, recorded13[0])
        assertEquals(sql23, recorded13[1])

        // 4. Verify registered migrations in AppDatabase
        val migrations = AppDatabase.ALL_MIGRATIONS
        assertEquals(3, migrations.size)
        assertTrue(migrations.contains(AppDatabase.MIGRATION_1_2))
        assertTrue(migrations.contains(AppDatabase.MIGRATION_2_3))
        assertTrue(migrations.contains(AppDatabase.MIGRATION_1_3))
    }

    @Test
    fun realSqliteMigration_preservesExistingData_whenSqliteCliAvailable() {
        if (!isSqliteCliAvailable()) {
            println("SKIP: sqlite3 CLI not available in environment; JVM migration contract verified above.")
            return
        }

        val tempFile = File.createTempFile("study_canvas_test_v1_v3", ".db")
        try {
            tempFile.delete()
            createV1Schema(tempFile)

            // Seed v1 data
            executeSqlite(
                tempFile,
                """
                INSERT INTO lessons (id, learnerId, title, level, status, worldWidth, worldHeight, createdAt, updatedAt)
                VALUES ('lesson-1', 'learner-1', '～たいです', 'N5', 'active', 2400.0, 3200.0, 1000, 1000);

                INSERT INTO ink_strokes (id, lessonId, exerciseElementId, sequence, toolType, brushJson, pointsJson, createdAt, updatedAt)
                VALUES ('stroke-1', 'lesson-1', 'exercise-1', 0, 'stylus', '{"family":"pen"}', '[{"x":10.0,"y":20.0}]', 1001, 1001);

                INSERT INTO exercise_attempts (id, learnerId, lessonId, exerciseElementId, recognizedText, correct, grammarScore, meaningScore, naturalnessScore, hintLevel, responseTimeMs, errorsJson, createdAt)
                VALUES ('attempt-1', 'learner-1', 'lesson-1', 'exercise-1', '日本に行きたいです', 1, 1.0, 1.0, 1.0, 0, 1200, '[]', 1002);
                """.trimIndent(),
            )

            // Run direct Migration 1 -> 3
            val testDb = createProxyDatabase(tempFile)
            AppDatabase.MIGRATION_1_3.migrate(testDb)

            // Assert attempt row is completely preserved and matchedAcceptedAnswer is accessible
            val attemptQuery = executeSqlite(
                tempFile,
                "SELECT id, recognizedText, correct, matchedAcceptedAnswer FROM exercise_attempts WHERE id = 'attempt-1';",
            )
            assertEquals("attempt-1|日本に行きたいです|1|", attemptQuery)

            // Assert ink stroke is completely preserved
            val strokeQuery = executeSqlite(
                tempFile,
                "SELECT id, lessonId, toolType FROM ink_strokes WHERE id = 'stroke-1';",
            )
            assertEquals("stroke-1|lesson-1|stylus", strokeQuery)

            // Assert generated_lessons table exists and accepts inserts
            executeSqlite(
                tempFile,
                """
                INSERT INTO generated_lessons (grammarId, generatorVersion, summary, formation, commonMistakesJson, notesJson, exercisesJson, generatedAt)
                VALUES ('lesson-1', 'v2-grounded', 'Summary', 'Formation', '[]', '[]', '[]', 2000);
                """.trimIndent(),
            )
            val generatedQuery = executeSqlite(
                tempFile,
                "SELECT grammarId, summary FROM generated_lessons WHERE grammarId = 'lesson-1';",
            )
            assertEquals("lesson-1|Summary", generatedQuery)
        } finally {
            tempFile.delete()
        }
    }

    private companion object {
        fun isSqliteCliAvailable(): Boolean = runCatching {
            val process = ProcessBuilder("sqlite3", "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        }.getOrDefault(false)

        fun createProxyDatabase(dbFile: File): SupportSQLiteDatabase {
            val handler = InvocationHandler { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        val sql = args[0] as String
                        executeSqlite(dbFile, sql)
                        null
                    }
                    "getPath" -> dbFile.absolutePath
                    "isOpen" -> true
                    "isReadOnly" -> false
                    else -> null
                }
            }
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java),
                handler,
            ) as SupportSQLiteDatabase
        }

        fun executeSqlite(dbFile: File, sql: String): String {
            val process = ProcessBuilder("sqlite3", dbFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(sql)
                it.newLine()
                it.flush()
            }
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error("sqlite3 failed with exit $exitCode: $output (SQL: $sql)")
            }
            return output.trim()
        }

        fun createV1Schema(dbFile: File) {
            val v1Schema = """
                CREATE TABLE IF NOT EXISTS `learner_profiles` (
                    `id` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `nativeLanguage` TEXT NOT NULL,
                    `targetLevel` TEXT,
                    `learningGoal` TEXT,
                    `teachingPreferencesJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                );
                CREATE TABLE IF NOT EXISTS `lessons` (
                    `id` TEXT NOT NULL,
                    `learnerId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `level` TEXT,
                    `status` TEXT NOT NULL,
                    `worldWidth` REAL NOT NULL,
                    `worldHeight` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                );
                CREATE TABLE IF NOT EXISTS `lesson_elements` (
                    `id` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `x` REAL NOT NULL,
                    `y` REAL NOT NULL,
                    `width` REAL NOT NULL,
                    `height` REAL NOT NULL,
                    `zIndex` INTEGER NOT NULL,
                    `readOnly` INTEGER NOT NULL,
                    `movable` INTEGER NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`lessonId`) REFERENCES `lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS `ink_strokes` (
                    `id` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `exerciseElementId` TEXT NOT NULL,
                    `sequence` INTEGER NOT NULL,
                    `toolType` TEXT NOT NULL,
                    `brushJson` TEXT NOT NULL,
                    `pointsJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`lessonId`) REFERENCES `lessons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                );
                CREATE TABLE IF NOT EXISTS `exercise_attempts` (
                    `id` TEXT NOT NULL,
                    `learnerId` TEXT NOT NULL,
                    `lessonId` TEXT NOT NULL,
                    `exerciseElementId` TEXT NOT NULL,
                    `recognizedText` TEXT NOT NULL,
                    `correct` INTEGER NOT NULL,
                    `grammarScore` REAL,
                    `meaningScore` REAL,
                    `naturalnessScore` REAL,
                    `hintLevel` INTEGER NOT NULL,
                    `responseTimeMs` INTEGER,
                    `errorsJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                );
                CREATE TABLE IF NOT EXISTS `learner_mastery` (
                    `id` TEXT NOT NULL,
                    `learnerId` TEXT NOT NULL,
                    `nodeId` TEXT NOT NULL,
                    `score` REAL NOT NULL,
                    `confidence` REAL NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `hintRate` REAL NOT NULL,
                    `lastPracticedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                );
                CREATE TABLE IF NOT EXISTS `tutor_memories` (
                    `id` TEXT NOT NULL,
                    `learnerId` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                );
                CREATE TABLE IF NOT EXISTS `review_schedule` (
                    `id` TEXT NOT NULL,
                    `learnerId` TEXT NOT NULL,
                    `nodeId` TEXT NOT NULL,
                    `dueAt` INTEGER NOT NULL,
                    `intervalDays` REAL NOT NULL,
                    `easeFactor` REAL NOT NULL,
                    `repetitions` INTEGER NOT NULL,
                    `state` TEXT NOT NULL,
                    `lastReviewedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                );
            """.trimIndent()
            executeSqlite(dbFile, v1Schema)
        }
    }
}
