"""Local marker artwork and image observations for fixture experiments, without pose/scoring."""
import cv2
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
import numpy as np

from scripts.feasibility.inspect_frame import decode_frame


def predefined_dictionary(name):
    value = getattr(cv2.aruco, name, None) if isinstance(name, str) and name.startswith('DICT_') else None
    if type(value) is not int:
        raise ValueError('Choose an OpenCV predefined DICT_* dictionary')
    return cv2.aruco.getPredefinedDictionary(value)


def validate_ids(ids, dictionary):
    if (not ids or len(set(ids)) != len(ids) or
            any(type(i) is not int or not 0 <= i < len(dictionary.bytesList) for i in ids)):
        raise ValueError('Marker IDs must be unique integers within the selected dictionary')


def marker_sheet(ids, dictionary='DICT_4X4_100', edge_mm=10, quiet_mm=2):
    selected = predefined_dictionary(dictionary)
    validate_ids(ids, selected)
    if any(type(v) not in (int, float) or not math.isfinite(v) or v <= 0 for v in (edge_mm, quiet_mm)):
        raise ValueError('Marker edge and white quiet zone must be positive finite millimeters')
    outer = edge_mm + 2*quiet_mm
    pitch = outer + 10
    columns, rows = int(190/pitch), int(257/pitch)
    if columns < 1 or rows < 1 or len(ids) > columns*rows:
        raise ValueError('These markers do not fit one A4 sheet; split IDs or reduce dimensions')
    root = ET.Element('svg', xmlns='http://www.w3.org/2000/svg', width='210mm', height='297mm', viewBox='0 0 210 297')
    ET.SubElement(root, 'rect', width='210', height='297', fill='white')
    ET.SubElement(root, 'text', x='10', y='10', **{'font-size': '3'}).text = f'{dictionary} | print 100%, measure black edges after printing'
    modules = selected.markerSize + 2
    cell = edge_mm/modules
    for index, marker_id in enumerate(ids):
        group = ET.SubElement(root, 'g', transform=f'translate({10+(index%columns)*pitch:g} {20+(index//columns)*pitch:g})',
            **{'data-marker-id': str(marker_id), 'data-black-edge-mm': f'{edge_mm:g}', 'data-quiet-zone-mm': f'{quiet_mm:g}'})
        bits = cv2.aruco.generateImageMarker(selected, marker_id, modules*20)[::20, ::20]
        for row, column in np.argwhere(bits == 0):
            ET.SubElement(group, 'rect', x=f'{quiet_mm+column*cell:.8f}', y=f'{quiet_mm+row*cell:.8f}',
                width=f'{cell:.8f}', height=f'{cell:.8f}', fill='black')
        ET.SubElement(group, 'text', x='0', y=f'{outer+4:g}', **{'font-size': '2.5'}).text = f'ID {marker_id} | top = +Y'
    return ET.tostring(root, encoding='unicode')


def observe_markers(image, dictionary='DICT_4X4_100', expected_ids=None):
    selected = predefined_dictionary(dictionary)
    if expected_ids is not None:
        validate_ids(expected_ids, selected)
    if (not isinstance(image, np.ndarray) or image.dtype != np.uint8 or image.size == 0 or
            image.ndim not in (2, 3) or (image.ndim == 3 and image.shape[2] not in (3, 4)) or
            image.shape[0]*image.shape[1] > 16_000_000):
        raise ValueError('Expected a nonempty uint8 grayscale/BGR/BGRA image of at most 16 megapixels')
    parameters = cv2.aruco.DetectorParameters()
    parameters.cornerRefinementMethod = cv2.aruco.CORNER_REFINE_SUBPIX
    corners, ids, rejected = cv2.aruco.ArucoDetector(selected, parameters).detectMarkers(image)
    observations = []
    if ids is not None:
        for marker_id, points in zip(ids.flatten(), corners):
            points = points.reshape(4, 2)
            lengths = np.linalg.norm(points-np.roll(points, -1, axis=0), axis=1)
            observations.append({'id': int(marker_id), 'corners_tl_tr_br_bl_px': points.tolist(),
                'area_px2': abs(float(cv2.contourArea(points))), 'minimum_edge_px': float(min(lengths)),
                'expected': expected_ids is None or int(marker_id) in expected_ids})
    counts = Counter(m['id'] for m in observations)
    return {'schema_version': 1, 'dictionary': dictionary, 'opencv_version': cv2.__version__,
        'numpy_version': np.__version__, 'image_width': image.shape[1], 'image_height': image.shape[0],
        'markers': sorted(observations, key=lambda m: m['id']),
        'duplicate_ids': sorted(i for i, count in counts.items() if count > 1),
        'rejected_candidates': [p.reshape(4, 2).tolist() for p in rejected],
        'measurement_valid': False,
        'interpretation': 'image observations only; no rigid-body pose, depth support or validated observability rule'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    generate = commands.add_parser('generate', help='Create one metric A4 SVG sheet')
    generate.add_argument('--ids', type=int, nargs='+', default=[21, 22, 23, 31, 32, 33])
    generate.add_argument('--edge-mm', type=float, default=10)
    generate.add_argument('--quiet-mm', type=float, default=2)
    generate.add_argument('--output-dir', type=Path, required=True)
    detect = commands.add_parser('detect', help='Observe PNG/JPEG or one exported feasibility JSON frame')
    detect.add_argument('image', type=Path)
    detect.add_argument('--expected-ids', type=int, nargs='+')
    detect.add_argument('--output-dir', type=Path, help='Explicitly save report and RGB annotation in a new directory')
    for command in (generate, detect):
        command.add_argument('--dictionary', default='DICT_4X4_100')
    args = parser.parse_args()
    try:
        if args.command == 'generate':
            sheet = marker_sheet(args.ids, args.dictionary, args.edge_mm, args.quiet_mm)
            args.output_dir.mkdir(parents=True, exist_ok=False)
            (args.output_dir/'markers.svg').write_text(sheet, encoding='utf-8')
            report = {'dictionary': args.dictionary, 'ids': args.ids, 'black_edge_mm': args.edge_mm,
                'quiet_zone_mm': args.quiet_mm, 'opencv_version': cv2.__version__,
                'status': 'candidate-artwork-print-and-mount-dimensions-unverified'}
            (args.output_dir/'marker-artwork.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        else:
            source = args.image.read_bytes()
            metadata = None
            if args.image.suffix.lower() == '.json':
                frame = decode_frame(json.loads(source))
                rgb, metadata = frame['rgb_png'], frame['metadata']
            else:
                rgb = source
            image = cv2.imdecode(np.frombuffer(rgb, dtype=np.uint8), cv2.IMREAD_COLOR)
            report = observe_markers(image, args.dictionary, args.expected_ids)
            if metadata is not None:
                if [report['image_width'], report['image_height']] != [metadata.get('rgbWidth'), metadata.get('rgbHeight')]:
                    raise ValueError('Decoded RGB dimensions disagree with export metadata')
                report['source_frame_id'] = metadata.get('frameID')
            report['source_sha256'] = hashlib.sha256(source).hexdigest()
            if args.output_dir:
                args.output_dir.mkdir(parents=True, exist_ok=False)
                annotation = image.copy()
                for marker in report['markers']:
                    points = [np.float32(marker['corners_tl_tr_br_bl_px']).reshape(1, 4, 2)]
                    cv2.aruco.drawDetectedMarkers(annotation, points, np.int32([[marker['id']]]))
                if not cv2.imwrite(str(args.output_dir/'annotated-rgb.png'), annotation):
                    raise OSError('Could not save RGB annotation')
                (args.output_dir/'observations.json').write_text(json.dumps(report, indent=2, allow_nan=False), encoding='utf-8')
        print(json.dumps(report, indent=2, allow_nan=False))
    except (OSError, ValueError, cv2.error) as error:
        parser.exit(1, f'Marker experiment rejected: {error}\n')


if __name__ == '__main__':
    main()
