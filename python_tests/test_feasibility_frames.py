import base64
import math
import struct
import unittest
from scripts.feasibility.inspect_frame import decode_frame, summarize_frame


def observation(depths=(0.4, 0.5, float('nan'), 0.6), accuracy='absolute'):
    return {
        'metadata': {'schemaVersion': 1, 'depthWidth': 2, 'depthHeight': 2,
                     'depthUnits': 'meters',
                     'depthEncoding': 'float32-little-endian-row-major-no-padding',
                     'depthAccuracy': accuracy, 'geometryUsable': True},
        'referenceDistanceMillimeters': 500,
        'rgbPNG': base64.b64encode(b'\x89PNG\r\n\x1a\n').decode(),
        'depthFloat32': base64.b64encode(struct.pack('<4f', *depths)).decode(),
    }


class FrameExportTests(unittest.TestCase):
    # Catches interpreting binary depth with padding, wrong units, or wrong size.
    def test_decode_preserves_invalid_samples_and_enforces_dimensions(self):
        decoded = decode_frame(observation())
        self.assertEqual(decoded['width'], 2)
        self.assertAlmostEqual(decoded['depths'][0], 0.4, places=6)
        self.assertTrue(math.isnan(decoded['depths'][2]))
        malformed = observation()
        malformed['metadata']['depthWidth'] = 3
        with self.assertRaises(ValueError):
            decode_frame(malformed)

    # Catches accepting malformed base64 or nonmetric export formats.
    def test_rejects_corrupt_payload_and_unknown_units(self):
        malformed = observation()
        malformed['depthFloat32'] = 'not_base64!'
        with self.assertRaises(ValueError):
            decode_frame(malformed)
        malformed = observation()
        malformed['metadata']['depthUnits'] = 'millimeters'
        with self.assertRaises(ValueError):
            decode_frame(malformed)

    # Catches invalid depth contaminating robust statistics and invented bias.
    def test_statistics_do_not_claim_bias_without_axial_reference_confirmation(self):
        result = summarize_frame(observation(), roi_fraction=1)
        self.assertEqual(result['roi_valid_count'], 3)
        self.assertEqual(result['roi_valid_ratio'], 0.75)
        self.assertAlmostEqual(result['roi_median_depth_mm'], 500, places=4)
        self.assertIsNone(result['axial_bias_mm'])
        confirmed = summarize_frame(observation(), roi_fraction=1, reference_is_axial_plane=True)
        self.assertAlmostEqual(confirmed['axial_bias_mm'], 0, places=4)

    def test_relative_depth_and_empty_roi_are_not_metric_accuracy_evidence(self):
        result = summarize_frame(observation(accuracy='relative'), roi_fraction=1, reference_is_axial_plane=True)
        self.assertIsNone(result['axial_bias_mm'])
        empty = summarize_frame(observation((0, -1, float('nan'), float('inf'))), roi_fraction=1)
        self.assertEqual(empty['roi_valid_count'], 0)
        self.assertIsNone(empty['roi_median_depth_mm'])


if __name__ == '__main__':
    unittest.main()
