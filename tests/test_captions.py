from backend.app import build_ass_subtitles


def test_build_ass_subtitles_creates_word_timed_karaoke_events():
    transcript = [{
        "start": 1.0, "end": 3.0, "text": "Hello world from ClipVlip",
        "words": [
            {"start": 1.0, "end": 1.4, "word": "Hello"},
            {"start": 1.5, "end": 1.9, "word": "world"},
            {"start": 2.0, "end": 2.2, "word": "from"},
            {"start": 2.3, "end": 3.0, "word": "ClipVlip"},
        ],
    }]
    ass = build_ass_subtitles(transcript, clip_start=1.0, clip_end=3.0, style="bold")
    assert "[Events]" in ass
    assert "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text" in ass
    assert "{\\kf50}Hello" in ass
    assert "{\\kf50}world" in ass
    assert "ClipVlip" in ass
    assert "Style: Default" in ass


def test_build_ass_subtitles_clamps_words_and_escapes_ass_controls():
    transcript = [{
        "start": 0.0, "end": 5.0, "text": "outside {unsafe} inside",
        "words": [
            {"start": 0.2, "end": 0.8, "word": "outside"},
            {"start": 1.2, "end": 1.8, "word": "{unsafe}"},
            {"start": 2.2, "end": 2.8, "word": "inside"},
        ],
    }]
    ass = build_ass_subtitles(transcript, clip_start=1.0, clip_end=3.0, style="clean")
    assert "outside" not in ass
    assert "\\{unsafe\\}" in ass
    assert "inside" in ass
    assert "1:00:00.00" not in ass


def test_build_ass_subtitles_uses_output_geometry_and_falls_back_to_known_style():
    ass = build_ass_subtitles([], clip_start=0, clip_end=10, style="not-a-style", ratio="16:9")
    assert "PlayResX: 1920" in ass
    assert "PlayResY: 1080" in ass
    assert "Style: Default,Arial,68," in ass
    assert "Dialogue:" not in ass
