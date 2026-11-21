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


def test_worker_base_url_saves_normalized_parsed_url():
    source = (Path(__file__).resolve().parents[1] / "frontend" / "app.js").read_text()
    assert 'normalized = parsed.href.replace(/\\/+$/, "")' in source
    assert 'localStorage.setItem("clipvlip_api_base", normalized)' in source
    assert 'localStorage.setItem("clipvlip_api_base", clean)' not in source


def test_library_task_metadata_is_rendered_as_text_not_html():
    source = (Path(__file__).resolve().parents[1] / "frontend" / "app.js").read_text()
    start = source.index("async function loadTasks()")
    end = source.index("async function openTask(", start)
    library_renderer = source[start:end]
    assert "container.replaceChildren()" in library_renderer
    assert "title.textContent = String(task.title" in library_renderer
    assert "meta.textContent = " in library_renderer
    assert "Number.isFinite(progress)" in library_renderer
    assert "${task.progress}" not in library_renderer
    assert "innerHTML" not in library_renderer
