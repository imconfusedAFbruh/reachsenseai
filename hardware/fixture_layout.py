"""Nominal prototype geometry, never a verified jig calibration."""
import argparse
import json
import math
from pathlib import Path
import subprocess


def candidate_layout(finger_radius_mm=8, marker_ids=None, face_angle_degrees=35):
    ids = [21, 22, 23] if marker_ids is None else marker_ids
    if len(ids) != 3 or len(set(ids)) != 3 or any(type(i) is not int or not 0 <= i < 100 for i in ids):
        raise ValueError('Exactly three unique DICT_4X4_100 candidate marker IDs are required')
    if not math.isfinite(finger_radius_mm) or not 6 <= finger_radius_mm <= 12:
        raise ValueError('Candidate radius must be between 6 and 12 mm; fit requires physical verification')
    if not math.isfinite(face_angle_degrees) or not 20 <= face_angle_degrees <= 45:
        raise ValueError('Candidate face angle must be between 20 and 45 degrees')
    marker_edge, quiet_zone = 10, 2
    face_edge, target_height = marker_edge+2*quiet_zone, 10
    base_z = finger_radius_mm+3
    theta = math.radians(face_angle_degrees)
    faces = []
    for i, sign in enumerate([0, 1, -1]):
        angle = sign*theta
        c, s = math.cos(angle), math.sin(angle)
        x = 0 if sign == 0 else sign*(face_edge/2+face_edge/2*math.cos(theta))
        z = base_z+target_height if sign == 0 else base_z+target_height-face_edge/2*math.sin(theta)
        transform = [[c, 0, s, x], [0, 1, 0, -18], [-s, 0, c, z], [0, 0, 0, 1]]
        local_corners = [[-5, 5, 0], [5, 5, 0], [5, -5, 0], [-5, -5, 0]]
        corners = [[sum(transform[row][col]*p[col] for col in range(3))+transform[row][3]
                    for row in range(3)] for p in local_corners]
        faces.append({'marker_id': ids[i], 'marker_edge_mm': marker_edge,
                      'transform_R_M_row_major': transform,
                      'corners_R_mm_tl_tr_br_bl': corners})
    return {
        'schema_version': 1, 'status': 'candidate-unmeasured-not-valid-for-scoring',
        'units': 'millimeters', 'dictionary': 'DICT_4X4_100',
        'frame': 'R: x-lateral, y-distal-along-finger, z-dorsal; origin at tip stop',
        'virtual_tip_mm': [0, 0, 0], 'faces': faces,
        'parameters': {'finger_radius_mm': finger_radius_mm, 'face_angle_degrees': face_angle_degrees,
                       'marker_edge_mm': marker_edge, 'quiet_zone_mm': quiet_zone,
                       'support_spacing_mm': 18, 'support_length_mm': 6,
                       'wall_mm': 2, 'target_height_mm': target_height},
        'required_verification': ['actual printed dimensions and marker placement',
            'two-region registration and gentle tip seating', 'endpoint drift and remount repeatability',
            'comfort, posture/reach effects, overlap interference', 'observability and depth coverage'],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--radius-mm', type=float, default=8)
    parser.add_argument('--face-angle-degrees', type=float, default=35)
    parser.add_argument('--ids', type=int, nargs=3, default=[21, 22, 23])
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--openscad', type=Path, help='Optional OpenSCAD CLI executable to render an STL')
    args = parser.parse_args()
    try:
        layout = candidate_layout(args.radius_mm, args.ids, args.face_angle_degrees)
        args.output_dir.mkdir(parents=True, exist_ok=False)
        (args.output_dir/'candidate-layout.json').write_text(json.dumps(layout, indent=2), encoding='utf-8')
        if args.openscad:
            source = Path(__file__).with_name('finger_fixture.scad')
            subprocess.run([str(args.openscad), '-o', str(args.output_dir/'finger-fixture-candidate.stl'),
                '-D', f'finger_radius_mm={args.radius_mm}', '-D', f'face_angle_degrees={args.face_angle_degrees}',
                str(source)], check=True)
        print('Candidate geometry exported; it is not a verified anatomical calibration.')
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'Candidate rejected: {error}\n')


if __name__ == '__main__':
    main()
