import 'package:shared_preferences/shared_preferences.dart';

/// Manages local persistence for app flags.
///
/// API keys and LLM connection data are stored exclusively in the backend;
/// the backend is the source of truth.  This service only persists
/// non-sensitive onboarding state.
class StorageService {
  static const _keyOnboardingDone = 'loom_onboarding_done';

  final SharedPreferences _prefs;

  StorageService(this._prefs);

  // ---------------------------------------------------------------------------
  // Onboarding flag (non-sensitive)
  // ---------------------------------------------------------------------------

  bool isOnboardingComplete() => _prefs.getBool(_keyOnboardingDone) ?? false;

  Future<void> completeOnboarding() => _prefs.setBool(_keyOnboardingDone, true);

  Future<void> resetOnboarding() async {
    await _prefs.setBool(_keyOnboardingDone, false);
  }
}
