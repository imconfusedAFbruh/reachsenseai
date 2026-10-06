import unittest
import xml.etree.ElementTree as ET
import json
from pathlib import Path
import subprocess
import sys
import tempfile

import cv2
import numpy as np

from scripts.feasibility.markers import marker_sheet, observe_markers


class MarkerExperimentTests(unittest.TestCase):
    def test_cli_exports_annotation_and_report_to_unicode_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            image = np.full((200, 200), 255, dtype=np.uint8)
            dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_100)
            image[40:160, 40:160] = cv2.aruco.generateImageMarker(dictionary, 21, 120)
            ok, encoded = cv2.imencode('.png', image)
            self.assertTrue(ok)
            source = root/'input.png'
            source.write_bytes(encoded.tobytes())
            output = root/'ทดสอบ'
            result = subprocess.run([sys.executable, '-m', 'scripts.feasibility.markers', 'detect',
                str(source), '--output-dir', str(output)], capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads((output/'observations.json').read_text(encoding='utf-8'))
            self.assertEqual([m['id'] for m in report['markers']], [21])
            png = (output/'annotated-rgb.png').read_bytes()
            self.assertEqual(cv2.imdecode(np.frombuffer(png, dtype=np.uint8), cv2.IMREAD_COLOR).shape, (200, 200, 3))

    def test_print_sheet_uses_metric_sizes_and_real_dictionary_bits(self):
        root = ET.fromstring(marker_sheet([21, 31]))
        self.assertEqual(root.attrib['width'], '210mm')
        groups = root.findall('{http://www.w3.org/2000/svg}g')
        self.assertEqual([g.attrib['data-marker-id'] for g in groups], ['21', '31'])
        self.assertEqual(groups[0].attrib['data-black-edge-mm'], '10')
        self.assertEqual(groups[0].attrib['data-quiet-zone-mm'], '2')
        cells = groups[0].findall('{http://www.w3.org/2000/svg}rect')
        bits = cv2.aruco.generateImageMarker(cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_100), 21, 120)[::20, ::20]
        self.assertEqual(len(cells), int(np.sum(bits == 0)))
        for kwargs in [{'ids': [21, 21]}, {'ids': [100]}, {'ids': []},
                       {'ids': [21], 'edge_mm': float('nan')},
                       {'ids': [21], 'dictionary': 'not-a-dictionary'}]:
            with self.assertRaises(ValueError):
                marker_sheet(**kwargs)

    def test_perspective_and_rotation_preserve_id_and_canonical_corner_order(self):
        dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_100)
        tag = cv2.aruco.generateImageMarker(dictionary, 21, 240)
        source = np.float32([[0, 0], [239, 0], [239, 239], [0, 239]])
        target = np.float32([[310, 65], [350, 290], [95, 330], [70, 105]])
        transform = cv2.getPerspectiveTransform(source, target)
        image = cv2.warpPerspective(tag, transform, (420, 400), borderValue=255)
        report = observe_markers(image, expected_ids=[21, 22, 23])
        self.assertEqual([m['id'] for m in report['markers']], [21])
        marker = report['markers'][0]
        np.testing.assert_allclose(marker['corners_tl_tr_br_bl_px'], target, atol=1.5)
        self.assertGreater(marker['area_px2'], 40_000)
        self.assertGreater(marker['minimum_edge_px'], 200)
        self.assertTrue(marker['expected'])
        self.assertNotIn('pose', report)
        self.assertFalse(report['measurement_valid'])

    def test_blank_and_duplicate_observations_do_not_become_rigid_body_acceptance(self):
        dictionary = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_100)
        image = np.full((220, 400), 255, dtype=np.uint8)
        tag = cv2.aruco.generateImageMarker(dictionary, 21, 120)
        image[50:170, 30:150] = tag
        image[50:170, 240:360] = tag
        report = observe_markers(image, expected_ids=[31, 32, 33])
        self.assertEqual(report['duplicate_ids'], [21])
        self.assertTrue(all(not m['expected'] for m in report['markers']))
        self.assertFalse(report['measurement_valid'])
        blank = observe_markers(np.full((100, 100), 255, dtype=np.uint8))
        self.assertEqual(blank['markers'], [])
        self.assertFalse(blank['measurement_valid'])
        with self.assertRaises(ValueError):
            observe_markers(np.empty((0, 0), dtype=np.uint8))


if __name__ == '__main__':
    unittest.main()
