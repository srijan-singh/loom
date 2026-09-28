import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages local persistence split by sensitivity:
/// - API keys → [FlutterSecureStorage] (OS-backed keychain)
/// - Flags (onboarding) → [SharedPreferences]
class StorageService {
  static const _keyApiKey = 'loom_api_key';
  static const _keyProvider = 'loom_provider';
  static const _keyModel = 'loom_model';
  static const _keyOnboardingDone = 'loom_onboarding_done';

  final FlutterSecureStorage _secure;
  final SharedPreferences _prefs;

  StorageService(this._secure, this._prefs);

  // ---------------------------------------------------------------------------
  // API key (secure)
  // ---------------------------------------------------------------------------

  Future<void> saveApiKey(String provider, String key, String model) async {
    await _secure.write(key: _keyApiKey, value: key);
    await _prefs.setString(_keyProvider, provider);
    await _prefs.setString(_keyModel, model);
  }

  Future<String?> getApiKey() => _secure.read(key: _keyApiKey);
  String? getProvider() => _prefs.getString(_keyProvider);
  String? getModel() => _prefs.getString(_keyModel);

  // ---------------------------------------------------------------------------
  // Onboarding flag (non-sensitive)
  // ---------------------------------------------------------------------------

  bool isOnboardingComplete() => _prefs.getBool(_keyOnboardingDone) ?? false;

  Future<void> completeOnboarding() => _prefs.setBool(_keyOnboardingDone, true);

  Future<void> resetOnboarding() async {
    await _prefs.setBool(_keyOnboardingDone, false);
    await _secure.delete(key: _keyApiKey);
    await _prefs.remove(_keyProvider);
    await _prefs.remove(_keyModel);
  }
}
