import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

class MockSecureStorage extends Fake implements FlutterSecureStorage {
  final Map<String, String> _data = {};

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (value != null) {
      _data[key] = value;
    }
  }

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    return _data[key];
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    _data.remove(key);
  }
}

void main() {
  group('StorageService', () {
    late StorageService storageService;
    late MockSecureStorage mockSecure;
    late SharedPreferences prefs;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      prefs = await SharedPreferences.getInstance();
      mockSecure = MockSecureStorage();
      storageService = StorageService(mockSecure, prefs);
    });

    test('saves and retrieves API key, provider, and model', () async {
      await storageService.saveApiKey('anthropic', 'sk-ant-12345', 'claude-3-5-sonnet');

      final key = await storageService.getApiKey();
      final provider = storageService.getProvider();
      final model = storageService.getModel();

      expect(key, 'sk-ant-12345');
      expect(provider, 'anthropic');
      expect(model, 'claude-3-5-sonnet');
    });

    test('manages onboarding lifecycle', () async {
      expect(storageService.isOnboardingComplete(), isFalse);

      await storageService.completeOnboarding();
      expect(storageService.isOnboardingComplete(), isTrue);

      await storageService.saveApiKey('openai', 'sk-test', 'gpt-4o');
      await storageService.resetOnboarding();

      expect(storageService.isOnboardingComplete(), isFalse);
      expect(await storageService.getApiKey(), isNull);
      expect(storageService.getProvider(), isNull);
      expect(storageService.getModel(), isNull);
    });
  });
}
