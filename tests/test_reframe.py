from backend.app import crop_filter


def test_vertical_crop_tracks_horizontal_subject_position():
    centred = crop_filter("9:16", 0.5)
    left = crop_filter("9:16", 0.25)
    assert "crop=1080:1920" in centred
    assert "0.5000" in centred
    assert "0.2500" in left


def test_square_crop_tracks_subject_and_clamps_focus():
    crop = crop_filter("1:1", 2.0)
    assert "crop=1080:1080" in crop
    assert "0.8500" in crop


def test_landscape_export_remains_letterboxed():
    crop = crop_filter("16:9", 0.2)
    assert "pad=1920:1080" in crop
    assert "crop=" not in crop
