package com.example.newstart.intervention

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

private data class AssessmentCatalogAsset(
    @SerializedName("scales")
    val scales: List<AssessmentScaleDefinition> = emptyList()
)

data class AssessmentScaleDefinition(
    val code: String,
    val title: String,
    val description: String,
    val freshnessDays: Int,
    val baseline: Boolean,
    val questions: List<AssessmentQuestionDefinition>,
    val severityBands: List<AssessmentSeverityBand>
)

data class AssessmentQuestionDefinition(
    val code: String,
    val prompt: String,
    val reverseScore: Boolean = false,
    val options: List<AssessmentOptionDefinition>
)

data class AssessmentOptionDefinition(
    val label: String,
    val value: Int
)

data class AssessmentSeverityBand(
    val min: Int,
    val max: Int,
    val label: String
) {
    fun matches(score: Int): Boolean = score in min..max
}

object AssessmentCatalog {
    private const val ASSET_PATH = "assessment_catalog.json"
    private val gson = Gson()

    val baselineScaleCodes = listOf("ISI", "ESS", "PSS10", "GAD7", "PHQ9", "WHO5")

    @Volatile
    private var cachedScales: List<AssessmentScaleDefinition>? = null

    fun all(context: Context): List<AssessmentScaleDefinition> {
        return cachedScales ?: synchronized(this) {
            cachedScales ?: load(context).also { cachedScales = it }
        }
    }

    fun baseline(context: Context): List<AssessmentScaleDefinition> {
        return all(context).filter { it.baseline }
    }

    fun find(context: Context, scaleCode: String): AssessmentScaleDefinition? {
        return all(context).firstOrNull { it.code == scaleCode }
    }

    private fun load(context: Context): List<AssessmentScaleDefinition> {
        context.assets.open(ASSET_PATH).use { input ->
            InputStreamReader(input).use { reader ->
                return gson.fromJson(reader, AssessmentCatalogAsset::class.java).scales
            }
        }
    }
}
