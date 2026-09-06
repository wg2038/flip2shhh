package shhh.cicada.flip

/**
 * Single source of truth for SharedPreferences storage, shared by
 * MainActivity, FlipToShhhService and BootReceiver.
 */
object PrefsKeys {
    const val PREFS_NAME = "flip_to_shhh_prefs"
    const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    const val KEY_DND_ACTIVE = "dnd_active"
    const val KEY_WAS_DND_ACTIVATED_BY_SERVICE = "was_dnd_activated_by_service"
    const val KEY_AUTO_LOCK_SCREEN = "auto_lock_screen"
    const val KEY_AUTO_START_BOOT = "auto_start_on_boot"
    const val KEY_SERVICE_USER_ENABLED = "service_user_enabled"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_LANGUAGE_MODE = "language_mode"
}
