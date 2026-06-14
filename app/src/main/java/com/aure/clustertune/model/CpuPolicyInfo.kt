package com.aure.clustertune.model

data class CpuPolicyInfo(
    val id: Int,
    val policyPath: String,
    val scalingMaxPath: String,
    val currentMaxFreq: Int,
    val selectableMaxFreq: Int,
    val observedMaxFreq: Int,
    val minFreq: Int,
    val supportedFrequencies: List<Int>,
    val cpuIds: List<Int> = listOf(id),
) {
    /**
     * Whether [frequency] is a valid `scaling_max_freq` target for this cluster: either a value
     * listed in `scaling_available_frequencies`, or a hardware turbo/boost frequency that sits
     * above the selectable table but at or below the observed hardware ceiling
     * (`cpuinfo_max_freq`). The latter lets bundled/stock profiles request true stock on SoCs
     * whose top "boost" bin is hidden from `scaling_available_frequencies` (e.g. SG8350P policy7,
     * selectable 3052800 but real max 3302400).
     */
    fun isWritableTarget(frequency: Int): Boolean {
        return frequency in supportedFrequencies ||
            (frequency in (selectableMaxFreq + 1)..observedMaxFreq)
    }
}
