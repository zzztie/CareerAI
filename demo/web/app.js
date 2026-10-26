"use strict";

const state = {
  token: null,
  userId: null,
  resumeId: null,
  jobId: null,
  jobTitle: null,
  busy: false
};

const el = (id) => document.getElementById(id);

function status(id, message, kind) {
  const node = el(id);
  node.textContent = message;
  node.classList.remove("is-error", "is-success");
  if (kind) node.classList.add("is-" + kind);
}

function updateSelection() {
  el("selected-resume").textContent = state.resumeId ? "#" + state.resumeId : "未选择";
  el("selected-job").textContent = state.jobId ? state.jobTitle + " · #" + state.jobId : "未选择";
  el("compare-button").disabled = state.busy || !state.token || !state.resumeId || !state.jobId;
}

async function api(path, options) {
  const response = await fetch(path, options || {});
  const text = await response.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!response.ok) {
    let message = "请求失败，HTTP " + response.status;
    if (response.status === 401) message = "登录已失效或无权访问，请重新登录。";
    if (response.status === 413) message = "文件超过上传大小限制。";
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      message = "后端暂时不可用，请检查 Compose 服务。";
    }
    if (data && typeof data === "object" && data.message && response.status !== 500) {
      message += " " + data.message;
    }
    throw new Error(message);
  }
  return data;
}

function authHeaders() {
  return { Authorization: "Bearer " + state.token };
}

async function login(event) {
  event.preventDefault();
  const email = el("email").value.trim();
  const password = el("password").value;
  if (!email || !password) return;
  el("login-button").disabled = true;
  status("session-state", "正在验证账号…");
  try {
    const result = await api("/hub/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json; charset=utf-8" },
      body: JSON.stringify({ email: email, password: password })
    });
    const token = result && result.data && result.data.token;
    if (!token) throw new Error("登录接口没有返回 Token。");
    state.token = token;
    el("password").value = "";
    const profile = await api("/hub/user/profile", { headers: authHeaders() });
    state.userId = profile && profile.data && profile.data.id;
    if (!state.userId) throw new Error("用户资料没有返回 userId。");
    status("session-state", "已登录 · 用户 #" + state.userId, "success");
    el("resume-select").disabled = false;
    el("resume-file").disabled = false;
    el("upload-button").disabled = false;
    await loadResumes();
  } catch (error) {
    state.token = null;
    state.userId = null;
    state.resumeId = null;
    status("session-state", error.message, "error");
  } finally {
    el("login-button").disabled = false;
    updateSelection();
  }
}

async function loadResumes(selectId) {
  const resumes = await api("/hub/resumes?userId=" + encodeURIComponent(state.userId), {
    headers: authHeaders()
  });
  const select = el("resume-select");
  select.replaceChildren();
  if (!Array.isArray(resumes) || resumes.length === 0) {
    select.add(new Option("暂无简历，请上传匿名 PDF", ""));
    state.resumeId = null;
    status("resume-state", "本账号暂无简历。");
  } else {
    resumes.forEach((resume) => {
      select.add(new Option((resume.filename || "简历") + " · #" + resume.id, String(resume.id)));
    });
    const chosen = selectId || state.resumeId || resumes[0].id;
    select.value = String(chosen);
    state.resumeId = Number(select.value);
    status("resume-state", "已读取 " + resumes.length + " 份简历。", "success");
  }
  updateSelection();
}

async function uploadResume() {
  const file = el("resume-file").files[0];
  if (!state.token) return status("resume-state", "请先登录。", "error");
  if (!file) return status("resume-state", "请先选择匿名 PDF。", "error");
  if (!file.name.toLowerCase().endsWith(".pdf") || file.size > 5 * 1024 * 1024) {
    return status("resume-state", "请选择不超过 5 MiB 的 PDF。", "error");
  }
  el("upload-button").disabled = true;
  status("resume-state", "正在上传 PDF…");
  try {
    const form = new FormData();
    form.append("file", file);
    const saved = await api("/hub/resumes/upload?userId=" + encodeURIComponent(state.userId), {
      method: "POST",
      headers: authHeaders(),
      body: form
    });
    await loadResumes(saved.id);
    status("resume-state", "上传成功，简历 #" + saved.id, "success");
    el("resume-file").value = "";
    document.querySelector(".file-label").textContent = "另选 PDF 文件";
  } catch (error) {
    status("resume-state", error.message, "error");
  } finally {
    el("upload-button").disabled = false;
    updateSelection();
  }
}

async function searchJobs() {
  const keyword = el("keyword").value.trim();
  const query = new URLSearchParams({ status: "OPEN", page: "1", size: "10" });
  if (keyword) query.set("keyword", keyword);
  el("search-button").disabled = true;
  const list = el("job-list");
  list.replaceChildren();
  const loading = document.createElement("p");
  loading.className = "hint";
  loading.textContent = "正在读取岗位…";
  list.append(loading);
  try {
    const page = await api("/hub/jobs/search?" + query.toString());
    const jobs = Array.isArray(page.records) ? page.records : [];
    list.replaceChildren();
    if (!jobs.length) {
      const empty = document.createElement("p");
      empty.className = "hint";
      empty.textContent = "没有找到在招岗位。请检查演示数据是否已导入。";
      list.append(empty);
      state.jobId = null;
      state.jobTitle = null;
    } else {
      jobs.forEach((job) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "job-card";
        button.setAttribute("aria-pressed", "false");
        const title = document.createElement("strong");
        title.textContent = job.title || "未命名岗位";
        const meta = document.createElement("span");
        meta.textContent = (job.company || "未知公司") + " · #" + job.id;
        button.append(title, meta);
        button.addEventListener("click", () => selectJob(job, button));
        list.append(button);
        if (state.jobId === Number(job.id)) selectJob(job, button);
      });
      if (!state.jobId) list.querySelector(".job-card").click();
    }
  } catch (error) {
    list.replaceChildren();
    const message = document.createElement("p");
    message.className = "hint is-error";
    message.textContent = error.message;
    list.append(message);
  } finally {
    el("search-button").disabled = false;
    updateSelection();
  }
}

function selectJob(job, button) {
  document.querySelectorAll(".job-card").forEach((item) => {
    item.classList.remove("is-selected");
    item.setAttribute("aria-pressed", "false");
  });
  button.classList.add("is-selected");
  button.setAttribute("aria-pressed", "true");
  state.jobId = Number(job.id);
  state.jobTitle = job.title || "未命名岗位";
  updateSelection();
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
}

function inline(text) {
  return escapeHtml(text)
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\[([RJK]\d+)\]/g, '<span class="cite">[$1]</span>');
}

function renderAnswer(markdown) {
  const root = el("answer-content");
  root.replaceChildren();
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  let i = 0;
  while (i < lines.length) {
    const line = lines[i].trim();
    if (!line) { i++; continue; }
    if (/^---+$/.test(line)) {
      root.append(document.createElement("hr")); i++; continue;
    }
    const heading = line.match(/^#{1,4}\s+(.+)$/);
    if (heading) {
      const node = document.createElement("h3");
      node.innerHTML = inline(heading[1]);
      root.append(node); i++; continue;
    }
    if (line.startsWith("|") && i + 1 < lines.length && /^\|?[\s:|\-]+\|?$/.test(lines[i + 1])) {
      const table = document.createElement("table");
      const head = document.createElement("tr");
      line.replace(/^\||\|$/g, "").split("|").forEach((cell) => {
        const th = document.createElement("th");
        th.innerHTML = inline(cell.trim());
        head.append(th);
      });
      table.append(head);
      i += 2;
      while (i < lines.length && lines[i].trim().startsWith("|")) {
        const row = document.createElement("tr");
        lines[i].trim().replace(/^\||\|$/g, "").split("|").forEach((cell) => {
          const td = document.createElement("td");
          td.innerHTML = inline(cell.trim());
          row.append(td);
        });
        table.append(row); i++;
      }
      root.append(table); continue;
    }
    if (/^-\s+/.test(line) || /^\d+\.\s+/.test(line) || /^\[[RJK]\d+\]\s+/.test(line)) {
      const ordered = /^\d+\.\s+/.test(line);
      const list = document.createElement(ordered ? "ol" : "ul");
      while (i < lines.length) {
        const current = lines[i].trim();
        const match = ordered
          ? current.match(/^\d+\.\s+(.+)$/)
          : (current.match(/^-\s+(.+)$/) || current.match(/^(\[[RJK]\d+\]\s+.+)$/));
        if (!match) break;
        const item = document.createElement("li");
        item.innerHTML = inline(match[1]);
        list.append(item); i++;
      }
      root.append(list); continue;
    }
    if (line.startsWith(">")) {
      const quote = document.createElement("blockquote");
      quote.innerHTML = inline(line.replace(/^>\s?/, ""));
      root.append(quote); i++; continue;
    }
    const paragraph = document.createElement("p");
    paragraph.innerHTML = inline(line);
    root.append(paragraph); i++;
  }
}

async function compare() {
  if (!state.token || !state.resumeId || !state.jobId) return;
  const question = el("question").value.trim();
  if (!question) return status("analysis-state", "请先输入问题。", "error");
  state.busy = true;
  updateSelection();
  el("answer-card").hidden = true;
  el("answer-empty").hidden = false;
  status("analysis-state", "正在下载简历、检索证据并生成回答；首次运行可能需要一些时间。");
  const started = performance.now();
  try {
    const result = await api("/careerai/ai/rag/compare/" + state.resumeId + "/" + state.jobId, {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json; charset=utf-8" }, authHeaders()),
      body: JSON.stringify({ question: question })
    });
    if (!result || !result.answer) throw new Error("分析接口没有返回回答。");
    renderAnswer(result.answer);
    el("elapsed").textContent = ((performance.now() - started) / 1000).toFixed(1) + " 秒";
    el("answer-empty").hidden = true;
    el("answer-card").hidden = false;
    status("analysis-state", "分析完成。请结合原文核对模型结论。", "success");
  } catch (error) {
    status("analysis-state", error.message, "error");
  } finally {
    state.busy = false;
    updateSelection();
  }
}

el("login-form").addEventListener("submit", login);
el("upload-button").addEventListener("click", uploadResume);
el("resume-select").addEventListener("change", (event) => {
  state.resumeId = Number(event.target.value) || null;
  updateSelection();
});
el("resume-file").addEventListener("change", (event) => {
  const file = event.target.files[0];
  document.querySelector(".file-label").textContent = file ? file.name : "另选 PDF 文件";
});
el("search-button").addEventListener("click", searchJobs);
el("keyword").addEventListener("keydown", (event) => {
  if (event.key === "Enter") searchJobs();
});
el("compare-button").addEventListener("click", compare);
updateSelection();
searchJobs();
