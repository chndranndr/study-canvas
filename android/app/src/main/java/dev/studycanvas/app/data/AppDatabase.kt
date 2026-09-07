package dev.studycanvas.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "learner_profiles")
data class LearnerProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val nativeLanguage: String = "id",
    val targetLevel: String? = null,
    val learningGoal: String? = null,
    val teachingPreferencesJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val learnerId: String = "default-learner",
    val title: String,
    val level: String? = null,
    val status: String = "active",
    val worldWidth: Float = 2400f,
    val worldHeight: Float = 3200f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "lesson_elements",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lessonId")],
)
data class LessonElementEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val kind: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int = 0,
    val readOnly: Boolean = false,
    val movable: Boolean = true,
    val payloadJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "ink_strokes",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lessonId"), Index("exerciseElementId")],
)
data class InkStrokeEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val exerciseElementId: String,
    val sequence: Int,
    val toolType: String = "stylus",
    val brushJson: String,
    val pointsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "exercise_attempts",
    indices = [Index("lessonId"), Index("exerciseElementId")],
)
data class ExerciseAttemptEntity(
    @PrimaryKey val id: String,
    val learnerId: String = "default-learner",
    val lessonId: String,
    val exerciseElementId: String,
    val recognizedText: String,
    val correct: Boolean,
    val matchedAcceptedAnswer: String? = null,
    val grammarScore: Float? = null,
    val meaningScore: Float? = null,
    val naturalnessScore: Float? = null,
    val hintLevel: Int = 0,
    val responseTimeMs: Long? = null,
    val errorsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "learner_mastery",
    indices = [Index(value = ["learnerId", "nodeId"], unique = true)],
)
data class LearnerMasteryEntity(
    @PrimaryKey val id: String,
    val learnerId: String = "default-learner",
    val nodeId: String,
    val score: Float = 0f,
    val confidence: Float = 0f,
    val attempts: Int = 0,
    val hintRate: Float = 0f,
    val lastPracticedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tutor_memories")
data class TutorMemoryEntity(
    @PrimaryKey val id: String,
    val learnerId: String = "default-learner",
    val category: String,
    val content: String,
    val confidence: Float = 0.5f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "review_schedule")
data class ReviewScheduleEntity(
    @PrimaryKey val id: String,
    val learnerId: String = "default-learner",
    val nodeId: String,
    val dueAt: Long,
    val intervalDays: Float = 1.0f,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
    val state: String = "new",
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
@Entity(tableName = "generated_lessons")
data class GeneratedLessonEntity(
    @PrimaryKey val grammarId: String,
    val generatorVersion: String = "gemini-1.5-flash",
    val summary: String = "",
    val formation: String = "",
    val commonMistakesJson: String = "[]",
    val notesJson: String = "[]",
    val exercisesJson: String = "[]",
    val generatedAt: Long = System.currentTimeMillis(),
)


@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun getLesson(id: String): LessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonEntity)

    @Query("SELECT * FROM lesson_elements WHERE lessonId = :lessonId ORDER BY zIndex ASC")
    suspend fun getElementsForLesson(lessonId: String): List<LessonElementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElements(elements: List<LessonElementEntity>)

    @Query("UPDATE lesson_elements SET x = :x, y = :y, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateElementPosition(id: String, x: Float, y: Float, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM lesson_elements WHERE lessonId = :lessonId")
    suspend fun deleteElementsForLesson(lessonId: String)
}

@Dao
interface InkDao {
    @Query("SELECT * FROM ink_strokes WHERE lessonId = :lessonId AND exerciseElementId = :exerciseElementId ORDER BY sequence ASC")
    suspend fun getStrokes(lessonId: String, exerciseElementId: String): List<InkStrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: InkStrokeEntity)

    @Query("DELETE FROM ink_strokes WHERE id = :id")
    suspend fun deleteStroke(id: String)

    @Query("DELETE FROM ink_strokes WHERE lessonId = :lessonId AND exerciseElementId = :exerciseElementId")
    suspend fun deleteStrokesForExercise(lessonId: String, exerciseElementId: String)

    @Query("SELECT COUNT(*) FROM ink_strokes WHERE lessonId = :lessonId AND exerciseElementId = :exerciseElementId")
    suspend fun countStrokes(lessonId: String, exerciseElementId: String): Int
}

@Dao
interface LearnerDao {
    @Query("SELECT * FROM learner_profiles WHERE id = :id")
    suspend fun getProfile(id: String): LearnerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LearnerProfileEntity)

    @Query("SELECT * FROM learner_mastery WHERE learnerId = :learnerId")
    suspend fun getMastery(learnerId: String): List<LearnerMasteryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMastery(mastery: LearnerMasteryEntity)
}

@Dao
interface AttemptDao {
    @Query("SELECT * FROM exercise_attempts WHERE exerciseElementId = :exerciseElementId ORDER BY createdAt DESC")
    suspend fun getAttempts(exerciseElementId: String): List<ExerciseAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: ExerciseAttemptEntity)
}

@Dao
interface TutorMemoryDao {
    @Query("SELECT * FROM tutor_memories WHERE learnerId = :learnerId ORDER BY updatedAt DESC")
    suspend fun getMemories(learnerId: String): List<TutorMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: TutorMemoryEntity)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_schedule WHERE learnerId = :learnerId AND dueAt <= :now ORDER BY dueAt ASC")
    suspend fun getDueReviews(learnerId: String, now: Long): List<ReviewScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(review: ReviewScheduleEntity)
}
@Dao
interface GeneratedLessonDao {
    @Query("SELECT * FROM generated_lessons WHERE grammarId = :grammarId")
    suspend fun getGeneratedLesson(grammarId: String): GeneratedLessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneratedLesson(lesson: GeneratedLessonEntity)

    @Query("DELETE FROM generated_lessons WHERE grammarId = :grammarId")
    suspend fun deleteGeneratedLesson(grammarId: String)
}


@Database(
    entities = [
        LearnerProfileEntity::class,
        LessonEntity::class,
        LessonElementEntity::class,
        InkStrokeEntity::class,
        ExerciseAttemptEntity::class,
        LearnerMasteryEntity::class,
        TutorMemoryEntity::class,
        ReviewScheduleEntity::class,
        GeneratedLessonEntity::class,
    ],
    version = 3,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun inkDao(): InkDao
    abstract fun learnerDao(): LearnerDao
    abstract fun attemptDao(): AttemptDao
    abstract fun tutorMemoryDao(): TutorMemoryDao
    abstract fun reviewDao(): ReviewDao
    abstract fun generatedLessonDao(): GeneratedLessonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_canvas.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }

        fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
