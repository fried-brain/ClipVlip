from pathlib import Path


def test_remote_worker_urls_require_https_and_allow_local_http():
    source = (Path(__file__).resolve().parents[1] / "frontend" / "app.js").read_text()
    assert 'parsed.protocol !== "https:"' in source
    assert 'parsed.protocol === "http:" && localHost' in source
    assert '["localhost", "127.0.0.1", "[::1]"]' in source


def test_clip_media_urls_are_created_with_dom_apis_not_html_interpolation():
    source = (Path(__file__).resolve().parents[1] / "frontend" / "app.js").read_text()
    assert "function renderClipResults(clips)" in source
    assert "document.createElement(\"video\")" in source
    assert "video.src = mediaUrl.href" in source
    assert "container.replaceChildren()" in source
    assert 'task.clips.map(clip => `<article class="clip-card">' not in source
