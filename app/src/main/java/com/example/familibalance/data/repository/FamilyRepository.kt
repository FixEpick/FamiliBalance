package com.example.familibalance.data.repository

import com.example.familibalance.data.model.ChildProfileState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository-слой для семейного состояния.
 *
 * ViewModel и actions работают только через этот интерфейс и не знают,
 * как именно хранятся данные (SharedPreferences / AppSharedState / Supabase).
 * AppSharedState остаётся внутренним Storage Layer.
 */
interface FamilyRepository {

    /** Статус синхронизации с облаком: false — есть неподтверждённые локальные изменения. */
    val isSynced: StateFlow<Boolean>

    /** Идёт ли сейчас сетевая отправка профилей в Supabase (push в полёте). */
    val isSyncing: StateFlow<Boolean>

    /** Эмитится при применении удалённого состояния (Realtime / первичная загрузка). */
    val onRemoteStateChanged: SharedFlow<Unit>

    /** Живой снимок профилей (snapshot-state), read-only view. */
    fun childrenSnapshot(): Map<String, ChildProfileState>

    fun getChild(userId: String): ChildProfileState?

    fun getAllChildren(): List<ChildProfileState>

    var selectedChildId: String

    /** Переключает активного ребёнка и сохраняет выбор (локально или с синхронизацией). */
    fun selectChild(childId: String, pushRemote: Boolean = false)

    /** Загружает состояние из локального хранилища. */
    fun loadState()

    /** Сохраняет состояние только локально (без push в Supabase). */
    fun saveLocal()

    /** Сохраняет локально и пушит профили в Supabase (опционально — только один). */
    fun saveAndSync(onlyChildId: String? = null)

    /** Применяет дневные/недельные сбросы квестов. */
    fun checkResets()

    /** Отправляет один профиль в Supabase (с загрузкой локальных фото в Storage). */
    fun pushChildProfile(child: ChildProfileState)

    /** Принудительная синхронизация: отправляет указанный профиль (или выбранного) и все dirty-профили. */
    fun syncNow(childId: String? = null)

    /** Единая точка обновления профиля: локальное сохранение + push в Supabase. */
    fun updateChildProfile(profile: ChildProfileState)

    /** Добавляет сообщение в лог уведомлений админа. */
    fun addAdminNotification(message: String)
}
