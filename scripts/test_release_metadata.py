import unittest
from release_metadata import metadata, MAX_VERSION_CODE

class ReleaseMetadataTest(unittest.TestCase):
    def test_tag_controls_name_and_android_version(self):
        self.assertEqual(metadata("v1.2.3"), {"tag": "v1.2.3", "version": "1.2.3", "version_code": 1002003, "apk_name": "FindMe-1.2.3.apk"})
    def test_minor_versions_have_zero_patch(self):
        self.assertEqual(metadata("v1.3")["version_code"], metadata("v1.3.0")["version_code"])
    def test_numeric_order_matches_android_order(self):
        tags = ["v1.2.3", "v1.2.10", "v1.3", "v1.10", "v2.0"]
        codes = [metadata(tag)["version_code"] for tag in tags]
        self.assertEqual(codes, sorted(set(codes)))
    def test_rejects_non_stable_tags_and_shell_text(self):
        for tag in ["1.2.3", "v1.2-beta", "v01.2.3", "v1.2.3.4", "v1.2.3;echo bad", "v1.2.3\nbad", "v0.0.0"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError): metadata(tag)
    def test_rejects_component_and_android_overflows(self):
        for tag in ["v1.1000.0", "v1.0.1000", "v9999.0.0"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError): metadata(tag)
        self.assertLessEqual(metadata("v2099.999.999")["version_code"], MAX_VERSION_CODE)
    def test_rejects_downgrades_and_equivalent_tags(self):
        for tag in ["v1.2.0", "v1.2.1", "v1.2"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError): metadata(tag, "v1.2.1")
        self.assertEqual(metadata("v1.2.2", "v1.2.1")["version_code"], 1002002)
    def test_first_automated_release_is_above_legacy_code(self):
        self.assertGreater(metadata("v1.2.2")["version_code"], 4)

if __name__ == "__main__":
    unittest.main()
