const $ = id => document.getElementById(id);
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
    const response = await fetch("/api/tasks", { method: "POST", body: data });
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
    const response = await fetch("/api/tasks/" + encodeURIComponent(currentTask));
    const task = await response.json();
    $("taskState").textContent = String(task.status).toUpperCase();
    $("progressBar").style.width = task.progress + "%";
    $("progressValue").textContent = task.progress + "%";
    $("taskMessage").textContent = task.message || "";
    if (task.status === "completed") {
      clearInterval(pollTimer);
      $("clipResults").innerHTML = task.clips.map(clip => `<article class="clip-card"><video controls playsinline src="${clip.url}"></video><div><b>${escapeHtml(clip.title)}</b><small>${Math.round(clip.start)}s – ${Math.round(clip.end)}s · ${Math.round(clip.end - clip.start)} sec</small><br><a href="${clip.url}" download="${escapeHtml(clip.filename)}">↓ Download MP4</a></div></article>`).join("");
      toast(task.clips.length + " clips are ready.");
    }
    if (task.status === "failed") {
      clearInterval(pollTimer);
      $("taskMessage").textContent = task.error || "Something went wrong";
      toast("Processing failed. Check the error message.");
    }
  } catch (error) { console.error(error); }
}
function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
}
async function loadTasks() {
  try {
    const response = await fetch("/api/tasks"), list = await response.json();
    $("tasks").innerHTML = list.length ? list.map(task => `<div class="task-row"><div><b>${escapeHtml(task.title || "Untitled project")}</b><small>${new Date(task.created * 1000).toLocaleString()} · ${escapeHtml(task.status)} · ${task.progress || 0}%</small></div><button data-task="${escapeHtml(task.id)}">Open project →</button></div>`).join("") : '<div class="panel"><p class="empty">No projects yet. Your next great clip starts with a video.</p></div>';
    $("tasks").querySelectorAll("[data-task]").forEach(button => button.addEventListener("click", () => openTask(button.dataset.task)));
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