import unittest

from check_gametest_count import RuntimeEvidenceError, verify_runtime_log

BATCH_HEADER = "Running test batch 'defaultBatch:0' (19 tests)...\n"


class CheckGameTestCountTest(unittest.TestCase):
    def test_exact_expected_count_passes(self):
        log = BATCH_HEADER + "=== 19 GAME TESTS COMPLETE IN 1.5s ===\nAll 19 required tests passed :)"
        self.assertEqual(verify_runtime_log(log), 19)

    def test_zero_tests_fails_closed(self):
        with self.assertRaises(RuntimeEvidenceError):
            verify_runtime_log("=== 0 GAME TESTS COMPLETE IN 0.1s ===\nAll 0 required tests passed :)")

    def test_fewer_than_expected_fails_closed(self):
        with self.assertRaises(RuntimeEvidenceError):
            verify_runtime_log("=== 18 GAME TESTS COMPLETE IN 1.0s ===\nAll 18 required tests passed :)")

    def test_startup_failure_fails_closed(self):
        with self.assertRaises(RuntimeEvidenceError):
            verify_runtime_log("Failed to start the minecraft server")

    def test_reported_required_failure_fails_closed(self):
        with self.assertRaises(RuntimeEvidenceError):
            verify_runtime_log(BATCH_HEADER
                                + "realvanilladirtblockgradesintoneighbourstandalone failed at 1, 2, 3! expected 5, got 0\n"
                                + "1 required tests failed :(")

    def test_mismatched_completed_and_passed_counts_fails_closed(self):
        with self.assertRaises(RuntimeEvidenceError):
            verify_runtime_log(BATCH_HEADER + "=== 19 GAME TESTS COMPLETE IN 1.5s ===\nAll 18 required tests passed :)")

    def test_missing_summary_fails_closed(self):
        with self.assertRaises(RuntimeEvidenceError):
            verify_runtime_log("nothing useful here")


if __name__ == "__main__":
    unittest.main()
