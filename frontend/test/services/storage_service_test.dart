import 'package:flutter_test/flutter_test.dart';
import 'package:loom_ui/services/storage_service.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  group('StorageService', () {
    late StorageService storageService;
    late SharedPreferences prefs;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      prefs = await SharedPreferences.getInstance();
      storageService = StorageService(prefs);
    });

    test('isOnboardingComplete returns false by default', () {
      expect(storageService.isOnboardingComplete(), isFalse);
    });

    test('completeOnboarding sets the flag to true', () async {
      await storageService.completeOnboarding();
      expect(storageService.isOnboardingComplete(), isTrue);
    });

    test('resetOnboarding clears the onboarding flag', () async {
      await storageService.completeOnboarding();
      expect(storageService.isOnboardingComplete(), isTrue);

      await storageService.resetOnboarding();
      expect(storageService.isOnboardingComplete(), isFalse);
    });

    test('manages onboarding lifecycle: false -> complete -> reset -> false',
        () async {
      expect(storageService.isOnboardingComplete(), isFalse);

      await storageService.completeOnboarding();
      expect(storageService.isOnboardingComplete(), isTrue);

      await storageService.resetOnboarding();
      expect(storageService.isOnboardingComplete(), isFalse);
    });
  });
}
