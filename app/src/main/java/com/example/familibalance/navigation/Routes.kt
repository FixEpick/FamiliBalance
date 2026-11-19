package com.example.familibalance.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object PlayerLoginRoute

@Serializable
data class RegisterParentRoute(val parentLogin: Boolean = false)

@Serializable
object AdminRoute

@Serializable
data class ChildRoute(val asParent: Boolean = false)

@Serializable
object ChildMainRoute

@Serializable
object ChildTasksRoute

@Serializable
object ChildAchievementsRoute

@Serializable
object ChildHistoryRoute

@Serializable
object AdminVerificationRoute

@Serializable
object AdminProgressRoute

@Serializable
object AdminAchievementsRoute

@Serializable
object AdminHistoryRoute

@Serializable
object AdminSettingsRoute

@Serializable
object AdminQuestsEditorRoute

@Serializable
object AdminAchievementsEditorRoute
