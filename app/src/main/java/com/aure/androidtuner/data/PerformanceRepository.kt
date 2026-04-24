package com.aure.androidtuner.data

import com.aure.androidtuner.model.CpuPolicyInfo
import com.aure.androidtuner.model.PerformanceProfile
import com.aure.androidtuner.model.PresetStateResolver
import com.aure.androidtuner.model.ProfileSource
import com.aure.androidtuner.model.TunerState
import com.aure.androidtuner.root.PerformanceCommandBuilder
import com.aure.androidtuner.root.RootCommandRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.util.UUID

class PerformanceRepository(
    private val detector: CpuPolicyDetector,
    private val bundledPresetProvider: BundledPresetProvider,
    private val profileStorage: ProfileStorage,
    private val commandBuilder: PerformanceCommandBuilder,
    private val rootCommandRunner: RootCommandRunner,
) {
    data class ApplyOutcome(
        val actualValues: Map<Int, Int>,
        val verificationPassed: Boolean,
        val commandOutput: String?,
    )

    private val refreshToken = MutableStateFlow(0)

    fun observeState(): Flow<TunerState> {
        val storageState = combine(
            profileStorage.profiles,
            profileStorage.deletedBundledProfileIds,
            profileStorage.displayOrder,
            profileStorage.lastValues,
            profileStorage.selectedProfileId,
        ) { storedProfiles, deletedBundledProfileIds, displayOrder, lastValues, selectedProfileId ->
            StorageState(
                storedProfiles = storedProfiles,
                deletedBundledProfileIds = deletedBundledProfileIds,
                displayOrder = displayOrder,
                lastValues = lastValues,
                selectedProfileId = selectedProfileId,
            )
        }
        return combine(
            refreshToken,
            storageState,
        ) { _, storage ->
            val policies = detector.detectPolicies()
            val defaultBundledProfiles = bundledPresetProvider.createProfiles(policies)
            val storedById = storage.storedProfiles.associateBy { it.id }
            val knownBundledIds = defaultBundledProfiles.map { it.id }.toSet()
            val bundledProfiles = defaultBundledProfiles.mapIndexed { index, profile ->
                if (profile.id in storage.deletedBundledProfileIds) {
                    null
                } else {
                    val stored = storedById[profile.id]
                    if (stored != null) {
                        profile.copy(
                            name = stored.name,
                            maxFrequencies = stored.maxFrequencies,
                            order = stored.order,
                            isEditable = true,
                            isDeletable = true,
                        )
                    } else {
                        profile.copy(
                            order = index,
                            isEditable = true,
                            isDeletable = true,
                        )
                    }
                }
            }.filterNotNull()
            val userProfiles = storage.storedProfiles
                .filter { it.source == ProfileSource.USER && it.id !in knownBundledIds }
            val orderedRealProfiles = applyDisplayOrder(
                profiles = bundledProfiles + userProfiles,
                orderedIds = storage.displayOrder,
            )
            val actualValues = policies.associate { it.id to it.currentMaxFreq }
            val defaultValues = policies.associate { it.id to it.currentMaxFreq }
            PresetStateResolver.resolve(
                TunerState(
                    isLoading = false,
                    isPServerAvailable = rootCommandRunner.isAvailable,
                    policies = policies,
                    actualValues = actualValues,
                    currentValues = mergeValues(policies, defaultValues, storage.lastValues),
                    bundledProfiles = orderedRealProfiles.filter { it.source == ProfileSource.BUNDLED },
                    userProfiles = orderedRealProfiles.filter { it.source == ProfileSource.USER },
                    selectedProfileId = storage.selectedProfileId?.takeIf { id ->
                        orderedRealProfiles.any { it.id == id }
                    },
                    displayProfiles = PresetStateResolver.buildDisplayProfiles(
                        realProfiles = orderedRealProfiles,
                        stockProfile = PresetStateResolver.buildStockProfile(policies),
                        orderedIds = storage.displayOrder,
                    ),
                ),
            )
        }
    }

    suspend fun applyValues(
        policies: List<CpuPolicyInfo>,
        selectedValues: Map<Int, Int>,
        isReset: Boolean,
    ): Result<ApplyOutcome> {
        val filtered = selectedValues.filterKeys { policyId -> policies.any { it.id == policyId } }
        val script = commandBuilder.buildApplyScript(policies, filtered, isReset)
        return rootCommandRunner.executeScript(script).mapCatching { output ->
            profileStorage.persistLastValues(filtered)
            val refreshedPolicies = detector.detectPolicies()
            val actualValues = refreshedPolicies.associate { it.id to it.currentMaxFreq }
            refresh()
            ApplyOutcome(
                actualValues = actualValues,
                verificationPassed = filtered.all { (policyId, requestedValue) ->
                    actualValues[policyId] == requestedValue
                },
                commandOutput = output,
            )
        }
    }

    suspend fun cycleTilePreset(): Result<PerformanceProfile> {
        val state = observeState().first()
        if (!state.isPServerAvailable || state.policies.isEmpty()) {
            return Result.failure(IllegalStateException("Tile controls are unavailable"))
        }

        val cycleProfiles = state.bundledProfiles + listOfNotNull(state.stockProfile)
        if (cycleProfiles.isEmpty()) {
            return Result.failure(IllegalStateException("No presets available for tile cycling"))
        }

        val currentIndex = cycleProfiles.indexOfFirst { it.id == state.activeDisplayProfileId }
        val nextProfile = if (currentIndex == -1) {
            cycleProfiles.lastOrNull { it.id == PresetStateResolver.STOCK_PROFILE_ID } ?: cycleProfiles.first()
        } else {
            cycleProfiles[(currentIndex + 1) % cycleProfiles.size]
        }

        return applyValues(
            policies = state.policies,
            selectedValues = nextProfile.maxFrequencies,
            isReset = nextProfile.id == PresetStateResolver.STOCK_PROFILE_ID,
        ).map {
            selectProfile(nextProfile.id.takeUnless { id -> id == PresetStateResolver.STOCK_PROFILE_ID })
            refresh()
            nextProfile
        }
    }

    suspend fun createUserPreset(name: String, values: Map<Int, Int>) {
        val currentProfiles = realProfiles()
        profileStorage.saveProfile(
            PerformanceProfile(
                id = "user_${UUID.randomUUID()}",
                name = name,
                maxFrequencies = values,
                source = ProfileSource.USER,
                order = currentProfiles.size,
            ),
        )
    }

    suspend fun updateProfile(profileId: String, name: String, values: Map<Int, Int>) {
        val existing = realProfiles().firstOrNull { it.id == profileId }
            ?: return
        if (existing.source == ProfileSource.BUNDLED) {
            profileStorage.unmarkBundledProfileDeleted(profileId)
        }
        profileStorage.saveProfile(
            existing.copy(
                name = name,
                maxFrequencies = values,
            ),
        )
    }

    suspend fun deleteProfile(profileId: String) {
        val existing = realProfiles().firstOrNull { it.id == profileId } ?: return
        if (existing.source == ProfileSource.BUNDLED) {
            profileStorage.markBundledProfileDeleted(profileId)
        } else {
            profileStorage.deleteProfile(profileId)
        }
        if (profileStorage.selectedProfileId.first() == profileId) {
            profileStorage.persistSelectedProfile(null)
        }
        refresh()
    }

    suspend fun moveProfile(profileId: String, offset: Int) {
        val state = observeState().first()
        val profiles = state.displayProfiles.toMutableList()
        val currentIndex = profiles.indexOfFirst { it.id == profileId }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + offset).coerceIn(0, profiles.lastIndex)
        if (currentIndex == targetIndex) return
        val profile = profiles.removeAt(currentIndex)
        profiles.add(targetIndex, profile)
        profileStorage.persistDisplayOrder(profiles.map { it.id })
        profileStorage.replaceProfiles(
            profiles
                .filter { it.source != ProfileSource.VIRTUAL }
                .mapIndexed { index, realProfile -> realProfile.copy(order = index) },
        )
    }

    suspend fun resetProfilesToDefault() {
        profileStorage.resetProfiles()
        profileStorage.persistSelectedProfile(null)
        refresh()
    }

    suspend fun selectProfile(profileId: String?) {
        profileStorage.persistSelectedProfile(profileId)
    }

    fun refresh() {
        refreshToken.update { it + 1 }
    }

    private fun mergeValues(
        policies: List<CpuPolicyInfo>,
        currentValues: Map<Int, Int>,
        persistedValues: Map<Int, Int>,
    ): Map<Int, Int> {
        return policies.associate { policy ->
            val supported = policy.supportedFrequencies.toSet()
            val persisted = persistedValues[policy.id]
            val safeValue = if (persisted != null && (persisted in supported || persisted == policy.stockMaxFreq)) {
                persisted
            } else {
                currentValues[policy.id] ?: policy.currentMaxFreq
            }
            policy.id to safeValue
        }
    }

    private suspend fun realProfiles(): List<PerformanceProfile> {
        val state = observeState().first()
        return state.displayProfiles.filter { it.source != ProfileSource.VIRTUAL }
    }

    private fun applyDisplayOrder(
        profiles: List<PerformanceProfile>,
        orderedIds: List<String>,
    ): List<PerformanceProfile> {
        if (orderedIds.isEmpty()) return profiles.sortedBy { it.order }
        val byId = profiles.associateBy { it.id }
        val ordered = orderedIds.mapNotNull(byId::get)
        val missing = profiles.filter { it.id !in orderedIds }.sortedBy { it.order }
        return ordered + missing
    }

    private data class StorageState(
        val storedProfiles: List<PerformanceProfile>,
        val deletedBundledProfileIds: Set<String>,
        val displayOrder: List<String>,
        val lastValues: Map<Int, Int>,
        val selectedProfileId: String?,
    )
}
