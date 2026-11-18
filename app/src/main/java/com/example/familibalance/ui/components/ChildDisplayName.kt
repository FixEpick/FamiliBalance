package com.example.familibalance.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.familibalance.R
import com.example.familibalance.data.model.ChildProfileState

/**
 * Отображаемое имя ребёнка в UI.
 * Data-слой не fabricate'ит локализованные строки: если имя пустое,
 * показываем «Ребёнок {id}» / "Child {id}" через stringResource.
 */
@Composable
fun childDisplayName(child: ChildProfileState): String =
    child.name.ifBlank { stringResource(R.string.default_child_name, child.id) }
