const API_BASE = (localStorage.getItem("clipvlip_api_base") || "").replace(/\/$/, "");
const apiUrl = path => API_BASE + path;
const apiHeaders = () => {
  const key = localStorage.getItem("clipvlip_access_key") || "";
  return key ? { "X-ClipVlip-Key": key } : {};
};
const $ = id => document.getElementById(id);
$("engineSettings").addEventListener("click", () => {
  const next = window.prompt("Enter your ClipVlip worker API URL (for example https://api.your-domain.com). Leave blank to use same-origin API.", API_BASE);
  if (next === null) return;
  const input = next.trim();
  let normalized = "";
  if (input) {
    let parsed;
    try { parsed = new URL(input); } catch { return toast("Enter a valid worker API URL."); }
    const localHost = ["localhost", "127.0.0.1", "[::1]"].includes(parsed.hostname);
    if (parsed.protocol !== "https:" && !(parsed.protocol === "http:" && localHost)) {
      return toast("Use HTTPS for remote workers. HTTP is allowed only for localhost development.");
    }
    if (parsed.username || parsed.password || parsed.search || parsed.hash) {
      return toast("Use a worker base URL without credentials, query parameters or fragments.");
    }
    normalized = parsed.href.replace(/\/+$/, "");
  }
  let accessKey = "";
  if (normalized) {
    accessKey = window.prompt("Enter the private worker access key configured in Render. This is stored only in this browser.", localStorage.getItem("clipvlip_access_key") || "");
    if (accessKey === null) return;
    accessKey = accessKey.trim();
    if (!accessKey) return toast("A worker access key is required.");
  }
  localStorage.setItem("clipvlip_access_key", accessKey);
  localStorage.setItem("clipvlip_api_base", normalized);
  window.location.reload();
});
let mode = "upload", currentTask = null, pollTimer = null;
function toast(message) {
  $("toast").textContent = message;
  $("toast").classList.add("show");
  window.setTimeout(() => $("toast").classList.remove("show"), 3000);
}
function showView(view) {
  $("createView").classList.toggle("hidden", view !== "create");
  $("libraryView").classList.toggle("hidden", view !== "library");
  $("activeTask").classList.add("hidden");
  $("pageTitle").innerHTML = view === "create" ? "Create something <em>viral.</em>" : "Your clips, in one place.";
  document.querySelectorAll(".nav").forEach(item => item.classList.toggle("active", item.dataset.view === view));
  if (view === "library") loadTasks();
}
document.querySelectorAll(".nav").forEach(item => item.addEventListener("click", () => showView(item.dataset.view)));
document.querySelectorAll(".tab").forEach(item => item.addEventListener("click", () => {
  mode = item.dataset.tab;
  document.querySelectorAll(".tab").forEach(tab => tab.classList.toggle("active", tab === item));
  $("uploadPane").classList.toggle("hidden", mode !== "upload");
  $("urlPane").classList.toggle("hidden", mode !== "url");
}));
$("browse").addEventListener("click", () => $("videoFile").click());
$("videoFile").addEventListener("change", () => { $("fileName").textContent = $("videoFile").files[0]?.name || ""; });
$("dropzone").addEventListener("click", event => { if (event.target.id !== "browse") $("videoFile").click(); });
$("uploadPane").addEventListener("dragover", event => { event.preventDefault(); $("uploadPane").style.borderColor = "#ad8aff"; });
$("uploadPane").addEventListener("drop", event => {
  event.preventDefault();
  const file = event.dataTransfer.files[0];
  if (file) { $("videoFile").files = event.dataTransfer.files; $("fileName").textContent = file.name; }
});
$("newProject").addEventListener("click", () => showView("create"));
$("generate").addEventListener("click", async () => {
  const file = $("videoFile").files[0], url = $("videoUrl").value.trim();
  if (mode === "upload" && !file) return toast("Choose a video file first.");
  if (mode === "url" && !url) return toast("Paste a video URL first.");
  const min = Number($("minDuration").value), max = Number($("maxDuration").value);
  if (min >= max) return toast("Maximum duration must exceed minimum duration.");
  const data = new FormData();
  if (mode === "upload") data.append("file", file); else data.append("source_url", url);
  data.append("clip_count", $("clipCount").value);
  data.append("aspect_ratio", $("ratio").value);
  data.append("min_duration", min);
  data.append("max_duration", max);
  data.append("captions", $("captions").checked ? "true" : "false");
  data.append("caption_style", $("captionStyle").value);
  const button = $("generate");
  button.disabled = true;
  button.textContent = "Starting your project…";
  try {
    const response = await fetch(apiUrl("/api/tasks"), { method: "POST", headers: apiHeaders(), body: data });
    const result = await response.json();
    if (!response.ok) throw new Error(result.detail || "Could not start processing");
    currentTask = result.id;
    $("activeTask").classList.remove("hidden");
    $("taskTitle").textContent = file?.name || url;
    $("clipResults").innerHTML = "";
    await poll();
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(poll, 2500);
    toast("Project started. ClipVlip is on it.");
  } catch (error) { toast(error.message); }
  finally {
    button.disabled = false;
    button.innerHTML = "<span>✦</span> Find my best moments <b>→</b>";
  }
});
async function poll() {
  if (!currentTask) return;
  try {
    const response = await fetch(apiUrl("/api/tasks/" + encodeURIComponent(currentTask)), { headers: apiHeaders() });
    const task = await response.json();
    $("taskState").textContent = String(task.status).toUpperCase();
    $("progressBar").style.width = task.progress + "%";
    $("progressValue").textContent = task.progress + "%";
    $("taskMessage").textContent = task.message || "";
    if (task.status === "completed") {
      clearInterval(pollTimer);
      renderClipResults(task.clips);
      toast(task.clips.length + " clips are ready.");
    }
    if (task.status === "failed") {
      clearInterval(pollTimer);
      $("taskMessage").textContent = task.error || "Something went wrong";
      toast("Processing failed. Check the error message.");
    }
  } catch (error) { console.error(error); }
}
function renderClipResults(clips) {
  const container = $("clipResults");
  container.replaceChildren();
  for (const clip of clips) {
    if (!clip || typeof clip.url !== "string" || !clip.url.startsWith("/api/clips/")) continue;
    const mediaUrl = new URL(apiUrl(clip.url), window.location.origin);
    const workerOrigin = API_BASE ? new URL(API_BASE).origin : window.location.origin;
    if (!["https:", "http:"].includes(mediaUrl.protocol) || mediaUrl.origin !== workerOrigin) continue;

    const article = document.createElement("article");
    article.className = "clip-card";
    const video = document.createElement("video");
    video.controls = true;
    video.playsInline = true;
    video.src = mediaUrl.href;
    const details = document.createElement("div");
    const title = document.createElement("b");
    title.textContent = String(clip.title || "Untitled clip");
    const timing = document.createElement("small");
    const start = Number(clip.start) || 0;
    const end = Number(clip.end) || 0;
    timing.textContent = `${Math.round(start)}s – ${Math.round(end)}s · ${Math.max(0, Math.round(end - start))} sec`;
    const lineBreak = document.createElement("br");
    const download = document.createElement("a");
    download.href = mediaUrl.href;
    download.download = String(clip.filename || "clip.mp4");
    download.textContent = "↓ Download MP4";
    details.append(title, timing, lineBreak, download);
    article.append(video, details);
    container.append(article);
  }
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
}
async function loadTasks() {
  try {
    const response = await fetch(apiUrl("/api/tasks"), { headers: apiHeaders() }), list = await response.json();
    const container = $("tasks");
    container.replaceChildren();
    if (!Array.isArray(list) || list.length === 0) {
      const panel = document.createElement("div");
      panel.className = "panel";
      const empty = document.createElement("p");
      empty.className = "empty";
      empty.textContent = "No projects yet. Your next great clip starts with a video.";
      panel.append(empty);
      container.append(panel);
      return;
    }
    for (const task of list) {
      const row = document.createElement("div");
      row.className = "task-row";
      const details = document.createElement("div");
      const title = document.createElement("b");
      title.textContent = String(task.title || "Untitled project");
      const meta = document.createElement("small");
      const createdRaw = task.created_at || task.created;
      const createdDate = typeof createdRaw === "string"
        ? new Date(createdRaw)
        : new Date(Number(createdRaw) * 1000);
      const createdLabel = Number.isFinite(createdDate.getTime())
        ? createdDate.toLocaleString() : "Date unavailable";
      const progress = Number(task.progress);
      const safeProgress = Number.isFinite(progress) ? Math.max(0, Math.min(100, progress)) : 0;
      meta.textContent = `${createdLabel} · ${String(task.status || "unknown")} · ${safeProgress}%`;
      details.append(title, meta);
      const button = document.createElement("button");
      button.textContent = "Open project →";
      button.addEventListener("click", () => openTask(String(task.id || "")));
      row.append(details, button);
      container.append(row);
    }
  } catch (error) { toast("Could not load project library."); }
}
async function openTask(id) {
  currentTask = id;
  showView("create");
  $("activeTask").classList.remove("hidden");
  $("clipResults").innerHTML = "";
  await poll();
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(poll, 2500);
}