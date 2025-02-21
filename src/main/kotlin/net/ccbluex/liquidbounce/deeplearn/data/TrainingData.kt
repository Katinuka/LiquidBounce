package net.ccbluex.liquidbounce.deeplearn.data

import com.google.gson.annotations.SerializedName
import net.ccbluex.liquidbounce.config.gson.util.decode
import net.ccbluex.liquidbounce.utils.aiming.data.Rotation
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import java.io.File

/**
 * The age defines the ticks we start tracking the entity. However, due to the fact
 * that in simulated environments the time of combat will be much shorter,
 * and therefore not have enough data to satisfy our whole scale of combat time.
 *
 * This limit will allow the model to know that we just started,
 * until we reach the maximum training age.
 */
const val MAXIMUM_TRAINING_AGE = 5

data class TrainingData(
    @SerializedName(CURRENT_DIRECTION_VECTOR)
    val currentVector: Vec3d,
    @SerializedName(PREVIOUS_DIRECTION_VECTOR)
    val previousVector: Vec3d,
    @SerializedName(TARGET_DIRECTION_VECTOR)
    val targetVector: Vec3d,
    @SerializedName(DELTA_VECTOR)
    val velocityDelta: Vec2f,

    @SerializedName(P_DIFF)
    val playerDiff: Vec3d,
    @SerializedName(T_DIFF)
    val targetDiff: Vec3d,

    @SerializedName(DISTANCE)
    val distance: Float,

    @SerializedName(HURT_TIME)
    val hurtTime: Int,
    /**
     * Age in this case is the Entity Age, however, we will use it later to determine
     * the time we have been tracking this entity.
     */
    @SerializedName(AGE)
    val age: Int
) {

    val currentRotation
        get() = Rotation.fromRotationVec(currentVector)
    val targetRotation
        get() = Rotation.fromRotationVec(targetVector)
    val previousRotation
        get() = Rotation.fromRotationVec(previousVector)

    /**
     * Total delta should be in a positive direction,
     * going from the current rotation to the target rotation.
     */
    val totalDelta
        get() = currentRotation.rotationDeltaTo(targetRotation)

    /**
     * Velocity delta should be in a positive direction,
     * going from the previous rotation to the current rotation.
     */
    val previousVelocityDelta
        get() = previousRotation.rotationDeltaTo(currentRotation)

    val asInput: FloatArray
        get() = floatArrayOf(
            // Total Delta
            totalDelta.deltaYaw,
            totalDelta.deltaPitch,

            // Velocity Delta
            previousVelocityDelta.deltaYaw,
            previousVelocityDelta.deltaPitch,

            // Speed
            targetDiff.horizontalLength().toFloat() + playerDiff.horizontalLength().toFloat(),

            // Distance
            distance.toFloat()
        )

    val asOutput
        get() = floatArrayOf(
            velocityDelta.x,
            velocityDelta.y
        )

    companion object {
        const val CURRENT_DIRECTION_VECTOR = "a"
        const val PREVIOUS_DIRECTION_VECTOR = "b"
        const val TARGET_DIRECTION_VECTOR = "c"
        const val DELTA_VECTOR = "d"
        const val HURT_TIME = "e"
        const val AGE = "f"
        const val P_DIFF = "g"
        const val T_DIFF = "h"
        const val DISTANCE = "i"

        fun parse(vararg file: File): List<TrainingData> {
            val files = file.flatMap { f ->
                if (f.isDirectory) {
                    f.listFiles { _, name -> name.endsWith(".json") }?.toList()
                        ?: emptyList()
                } else {
                    listOf(f)
                }
            }

            return files.flatMap { file ->
                file.inputStream().use { stream ->
                    decode<List<TrainingData>>(stream)
                }
            }
        }

    }
}
