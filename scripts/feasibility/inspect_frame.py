"""Inspect actual sensor exports without converting them into validated test scores."""
import argparse
import base64
import binascii
import json
import math
from pathlib import Path
import statistics
import struct


def decode_frame(record):
    try:
        metadata = record['metadata']
        width, height = metadata['depthWidth'], metadata['depthHeight']
        if metadata['schemaVersion'] != 1:
            raise ValueError('Unsupported export schema')
        if (type(width) is not int or type(height) is not int or
                width <= 0 or height <= 0 or width*height > 16_000_000):
            raise ValueError('Invalid depth dimensions')
        if metadata['depthUnits'] != 'meters':
            raise ValueError('Expected metric depth in meters')
        if metadata['depthEncoding'] != 'float32-little-endian-row-major-no-padding':
            raise ValueError('Unsupported binary depth encoding')
        depth_bytes = base64.b64decode(record['depthFloat32'], validate=True)
        rgb = base64.b64decode(record['rgbPNG'], validate=True)
        if len(depth_bytes) != width*height*4:
            raise ValueError('Depth payload size does not match dimensions')
        if not rgb.startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError('RGB payload is not PNG')
        depths = tuple(value[0] for value in struct.iter_unpack('<f', depth_bytes))
        return {'width': width, 'height': height, 'depths': depths,
                'depth_bytes': depth_bytes, 'rgb_png': rgb, 'metadata': metadata}
    except (KeyError, TypeError, binascii.Error) as error:
        raise ValueError('Malformed feasibility export') from error


def summarize_frame(record, roi_fraction=0.2, reference_is_axial_plane=False):
    if not isinstance(roi_fraction, (int, float)) or not math.isfinite(roi_fraction) or not 0 < roi_fraction <= 1:
        raise ValueError('ROI fraction must be between zero and one')
    frame = decode_frame(record)
    width, height, depths = frame['width'], frame['height'], frame['depths']
    roi_width = max(1, int(width*roi_fraction))
    roi_height = max(1, int(height*roi_fraction))
    x0, y0 = width//2-roi_width//2, height//2-roi_height//2
    roi = [depths[y*width+x] for y in range(y0, y0+roi_height) for x in range(x0, x0+roi_width)]
    valid = [d*1000 for d in roi if math.isfinite(d) and d > 0]
    median = statistics.median(valid) if valid else None
    bias = None
    reference = record.get('referenceDistanceMillimeters')
    if (reference_is_axial_plane and frame['metadata'].get('depthAccuracy') == 'absolute' and
            median is not None and type(reference) in (int, float) and math.isfinite(reference) and reference > 0):
        bias = median-reference
    return {
        'frame_id': frame['metadata'].get('frameID'),
        'hardware_identifier': frame['metadata'].get('hardwareIdentifier'),
        'depth_width': width, 'depth_height': height,
        'roi_bounds_xywh': [x0, y0, roi_width, roi_height],
        'roi_valid_count': len(valid), 'roi_sample_count': len(roi),
        'roi_valid_ratio': len(valid)/len(roi),
        'roi_median_depth_mm': median,
        'roi_spatial_sd_mm': statistics.pstdev(valid) if valid else None,
        'axial_bias_mm': bias,
        'geometry_usable': frame['metadata'].get('geometryUsable', False),
        'interpretation': 'sensor characterization only; spatial SD is not temporal repeatability or fingertip accuracy',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('frame', type=Path, help='One JSON observation saved by the iOS feasibility app')
    parser.add_argument('--roi-fraction', type=float, default=0.2)
    parser.add_argument('--reference-is-axial-plane', action='store_true',
                        help='Confirm independently measured reference is axial depth of a planar target filling the ROI')
    parser.add_argument('--extract-dir', type=Path, help='New directory for decoded PNG/depth/metadata; existing directories are refused')
    args = parser.parse_args()
    try:
        record = json.loads(args.frame.read_text(encoding='utf-8'))
        summary = summarize_frame(record, args.roi_fraction, args.reference_is_axial_plane)
        if args.extract_dir:
            decoded = decode_frame(record)
            args.extract_dir.mkdir(parents=True, exist_ok=False)
            (args.extract_dir/'rgb.png').write_bytes(decoded['rgb_png'])
            (args.extract_dir/'depth.float32le').write_bytes(decoded['depth_bytes'])
            (args.extract_dir/'metadata.json').write_text(json.dumps(decoded['metadata'], indent=2), encoding='utf-8')
        print(json.dumps(summary, indent=2, allow_nan=False))
    except (OSError, ValueError) as error:
        parser.exit(1, f'Observation rejected: {error}\n')


if __name__ == '__main__':
    main()
