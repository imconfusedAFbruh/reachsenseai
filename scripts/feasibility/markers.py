"""Local marker artwork and image observations for fixture experiments, without pose/scoring."""
import cv2


def marker_sheet(ids, dictionary='DICT_4X4_100', edge_mm=10, quiet_mm=2):
    return ''


def observe_markers(image, dictionary='DICT_4X4_100', expected_ids=None):
    return {'markers': [], 'duplicate_ids': [], 'rejected_candidates': []}
