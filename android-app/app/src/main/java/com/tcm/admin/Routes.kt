package com.tcm.admin

import kotlinx.serialization.Serializable

internal sealed class Route {
    @Serializable object Login : Route()
    @Serializable object Prescriptions : Route()
    @Serializable object E6Imports : Route()
    @Serializable data class E6ImportDetail(val id: Int) : Route()
    @Serializable data class E6ImportConfirm(val argId: String, val mergeIds: List<Int> = emptyList()) : Route()
    @Serializable data class PrescriptionDetail(val id: Int) : Route()
    @Serializable data class PrescriptionEdit(val argId: String) : Route()
    @Serializable object Processing : Route()
    @Serializable data class ProcessingPlanForm(val argId: String) : Route()
    @Serializable data class WorkflowOperation(val argId: String, val currentStep: String, val action: String) : Route()
    @Serializable object Packages : Route()
    @Serializable data class PackageDetail(val argId: String) : Route()
    @Serializable data class PackageForm(val argId: String) : Route()
    @Serializable data class PackageVerify(val initialCode: String = "") : Route()
    @Serializable object Herbs : Route()
    @Serializable data class HerbLocationAssign(val argId: String, val storeId: Int?) : Route()
    @Serializable object Profile : Route()
    @Serializable object ProfileDetail : Route()
    @Serializable object Settings : Route()
    @Serializable object ThemeAppearance : Route()
    @Serializable object About : Route()
    @Serializable data class Inventory(val initialQuery: String = "", val scanRequestId: Long = 0L) : Route()
    @Serializable object Stocktaking : Route()
    @Serializable data class StocktakingDetail(val checkId: Int) : Route()
    @Serializable object Differences : Route()
    @Serializable object Transfers : Route()
    @Serializable data class TransferDetail(val id: Int) : Route()
}
