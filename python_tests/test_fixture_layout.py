import unittest
from hardware.fixture_layout import candidate_layout


class FixtureLayoutTests(unittest.TestCase):
    # Catches treating all faces as coplanar or confusing jig coordinates with tip coordinates.
    def test_three_faces_share_one_frame_with_distinct_normals(self):
        result = candidate_layout()
        self.assertEqual(result['virtual_tip_mm'], [0, 0, 0])
        faces = result['faces']
        self.assertEqual([f['marker_id'] for f in faces], [21, 22, 23])
        center = faces[0]['transform_R_M_row_major']
        self.assertEqual([center[i][3] for i in range(3)], [0, -18, 21])
        right = faces[1]['transform_R_M_row_major']
        left = faces[2]['transform_R_M_row_major']
        self.assertAlmostEqual(right[0][2], 0.5735764364, places=8)
        self.assertAlmostEqual(left[0][2], -0.5735764364, places=8)
        self.assertAlmostEqual(right[2][2], 0.8191520443, places=8)
        self.assertEqual(result['status'], 'candidate-unmeasured-not-valid-for-scoring')

    # Catches printer/dimension changes silently leaving endpoint calibration unchanged.
    def test_size_changes_move_faces_but_do_not_move_registration_stop(self):
        result = candidate_layout(finger_radius_mm=10)
        self.assertEqual(result['virtual_tip_mm'], [0, 0, 0])
        self.assertEqual(len(result['faces']), 3)
        self.assertEqual(result['faces'][0]['transform_R_M_row_major'][2][3], 23)

    def test_duplicate_ids_and_impossible_geometry_are_rejected(self):
        with self.assertRaises(ValueError):
            candidate_layout(marker_ids=[21, 21, 23])
        with self.assertRaises(ValueError):
            candidate_layout(finger_radius_mm=-1)
        with self.assertRaises(ValueError):
            candidate_layout(face_angle_degrees=0)


if __name__ == '__main__':
    unittest.main()
