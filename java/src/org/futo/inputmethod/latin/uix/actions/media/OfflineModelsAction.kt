package org.futo.inputmethod.latin.uix.actions.media

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.settings.SettingsActivity

/**
 * Official offline KeyboardLM manager. FUTO imports only compatible model
 * metadata; this deliberately does not pretend arbitrary GGUF chat models work.
 */
val OfflineModelsAction = Action(
    icon = R.drawable.cpu,
    name = R.string.media_offline_models,
    canShowKeyboard = false,
    simplePressImpl = { manager, _ ->
        SettingsActivity.openToNavDest(manager.getContext(), "models")
    },
    windowImpl = null
)
