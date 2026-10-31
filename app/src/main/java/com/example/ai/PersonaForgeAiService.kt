package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "PersonaForgeAiService"

data class ExtractedFacialIdentity(
    val faceShape: String,
    val eyeGeometry: String,
    val browArch: String,
    val noseBridge: String,
    val lipContour: String,
    val complexion: String,
    val hairSignature: String,
    val distinguishingMarks: String
) {
    fun toCompactDescriptor(): String {
        return "Face: $faceShape | Eyes: $eyeGeometry | Brows: $browArch | Nose: $noseBridge | Lips: $lipContour | Skin: $complexion | Hair: $hairSignature | Marks: $distinguishingMarks"
    }
}

data class StoryboardScene(
    val sceneNumber: Int,
    val shotType: String,
    val cameraMovement: String,
    val emotionBeat: String,
    val visualPrompt: String
)

class PersonaForgeAiService {

    private val facialDnaSchema = Schema.obj(
        properties = mapOf(
            "faceShape" to Schema.string(description = "Jawline and overall facial bone structure"),
            "eyeGeometry" to Schema.string(description = "Eye shape, spacing, and iris color"),
            "browArch" to Schema.string(description = "Eyebrow thickness and arch profile"),
            "noseBridge" to Schema.string(description = "Nose bridge and tip geometry"),
            "lipContour" to Schema.string(description = "Lip proportion and contour"),
            "complexion" to Schema.string(description = "Skin tone and surface texture"),
            "hairSignature" to Schema.string(description = "Hair color, parting, and texture"),
            "distinguishingMarks" to Schema.string(description = "Unique facial landmarks or freckles")
        )
    )

    private val storyboardSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Storyboard sequence title"),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "sceneNumber" to Schema.integer(description = "1-based scene number"),
                        "shotType" to Schema.string(description = "Lens focal length and framing"),
                        "cameraMovement" to Schema.string(description = "Dolly, pan, or tracking motion"),
                        "emotionBeat" to Schema.string(description = "Character emotional expression"),
                        "visualPrompt" to Schema.string(description = "Detailed PG-13 frame synthesis prompt with locked facial traits")
                    )
                ),
                description = "4 sequential storyboard keyframes"
            )
        )
    )

    // Lazy initialization per firebase-ai-logic-android mandatory rule
    private val facialDnaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = facialDnaSchema
            }
        )
    }

    private val storyboardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
            }
        )
    }

    private val fastImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val proImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3-pro-image",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun extractFacialDnaFromReference(referenceBitmap: Bitmap?): Result<ExtractedFacialIdentity> {
        return try {
            val response = withTimeout(60_000L) {
                if (referenceBitmap != null) {
                    facialDnaModel.generateContent(
                        content {
                            image(referenceBitmap)
                            text(
                                "Analyze this consented studio reference portrait and extract a precise, objective facial identity lock descriptor for maintaining character consistency across emotional expressions."
                            )
                        }
                    )
                } else {
                    facialDnaModel.generateContent(
                        "Generate a realistic, distinctive studio character facial identity lock profile for a virtual creator persona."
                    )
                }
            }
            val jsonText = response.text ?: throw IllegalStateException("Model returned an empty response.")
            val json = JSONObject(jsonText)
            Result.success(
                ExtractedFacialIdentity(
                    faceShape = json.optString("faceShape", "Balanced oval jawline"),
                    eyeGeometry = json.optString("eyeGeometry", "Almond deep-set hazel eyes"),
                    browArch = json.optString("browArch", "Defined natural arch"),
                    noseBridge = json.optString("noseBridge", "Straight nose bridge"),
                    lipContour = json.optString("lipContour", "Balanced cupid's bow"),
                    complexion = json.optString("complexion", "Warm olive studio complexion"),
                    hairSignature = json.optString("hairSignature", "Dark espresso waves"),
                    distinguishingMarks = json.optString("distinguishingMarks", "Subtle beauty mark below left eye")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Facial DNA analysis timed out", e)
            Result.failure(IllegalStateException("Facial DNA extraction timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Facial DNA analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateConsistentCharacterImage(
        modelId: String,
        referenceBitmap: Bitmap?,
        personaName: String,
        facialDescriptor: String,
        emotion: String,
        emotionIntensity: Int,
        stylePreset: String,
        scenePrompt: String
    ): Result<Bitmap> {
        val activeModel = if (modelId == "gemini-3-pro-image") proImageModel else fastImageModel
        val compiledPrompt = buildString {
            append("PG-13 Pro Studio Character Portrait of ")
            append(personaName)
            append(". STRICT FACIAL IDENTITY LOCK (maintain exact same person and bone structure): ")
            append(facialDescriptor)
            append(". EMOTIONAL EXPRESSION: ")
            append(emotion)
            append(" at ")
            append(emotionIntensity)
            append("% intensity. CINEMATOGRAPHY & STYLE: ")
            append(stylePreset)
            append(". SCENE DIRECTION: ")
            append(scenePrompt)
        }

        return try {
            val bitmap = withTimeout(60_000L) {
                val response = if (referenceBitmap != null) {
                    activeModel.generateContent(
                        content {
                            image(referenceBitmap)
                            text(compiledPrompt)
                        }
                    )
                } else {
                    activeModel.generateContent(compiledPrompt)
                }
                response.candidates.firstOrNull()
                    ?.content?.parts?.filterIsInstance<ImagePart>()
                    ?.firstOrNull()?.image
            }
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("No image was returned by $modelId."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateVideoStoryboard(
        personaName: String,
        facialDescriptor: String,
        emotionArc: String,
        stylePreset: String,
        conceptPrompt: String
    ): Result<List<StoryboardScene>> {
        val prompt = """
            Create a 4-scene PG-13 creator video storyboard for persona '$personaName'.
            Locked Facial Identity: $facialDescriptor
            Emotional Progression: $emotionArc
            Visual Style: $stylePreset
            Concept: $conceptPrompt
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                storyboardModel.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty storyboard response.")
            val json = JSONObject(jsonText)
            val scenesArr = json.getJSONArray("scenes")
            val scenes = (0 until scenesArr.length()).map { idx ->
                val obj = scenesArr.getJSONObject(idx)
                StoryboardScene(
                    sceneNumber = obj.optInt("sceneNumber", idx + 1),
                    shotType = obj.optString("shotType", "35mm Medium Close-Up"),
                    cameraMovement = obj.optString("cameraMovement", "Slow push-in"),
                    emotionBeat = obj.optString("emotionBeat", emotionArc),
                    visualPrompt = obj.optString("visualPrompt", "")
                )
            }
            Result.success(scenes)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Video storyboard timed out", e)
            Result.failure(IllegalStateException("Storyboard generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video storyboard generation failed", e)
            Result.failure(e)
        }
    }
}
