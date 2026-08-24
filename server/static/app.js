const TOKEN_KEY = "hair_clinic_token";

const state = {
  token: localStorage.getItem(TOKEN_KEY) || "",
  customers: [],
  projects: [],
  orders: [],
};

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));
const PROJECT_UNITS = ["支", "个", "盒", "次"];

function projectUnit(value) {
  const raw = (value || "").trim();
  if (!raw || raw === "单位") return "次";
  return PROJECT_UNITS.includes(raw) ? raw : "个";
}

async function api(path, options = {}) {
  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const res = await fetch(path, { ...options, headers });
  if (res.status === 401) {
    logout(false);
    throw new Error("未登录或登录已过期");
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || "请求失败");
  return data;
}

function showApp(loggedIn) {
  $("#login-view").classList.toggle("hidden", loggedIn);
  $("#app-view").classList.toggle("hidden", !loggedIn);
}

function logout(clear = true) {
  if (clear) {
    state.token = "";
    localStorage.removeItem(TOKEN_KEY);
  }
  showApp(false);
}

function switchPage(name) {
  $$(".page").forEach((el) => el.classList.add("hidden"));
  $(`#page-${name}`).classList.remove("hidden");
  $$(".nav-btn").forEach((btn) => btn.classList.toggle("active", btn.dataset.page === name));
  if (name === "customers") loadCustomers();
  if (name === "projects") loadProjects();
  if (name === "inventory") loadInventory();
  if (name === "billing") loadBilling();
  if (name === "orders") loadOrders();
}

async function login(e) {
  e.preventDefault();
  $("#login-error").textContent = "";
  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: $("#login-username").value.trim(),
        password: $("#login-password").value,
      }),
    });
    state.token = data.access_token;
    localStorage.setItem(TOKEN_KEY, state.token);
    showApp(true);
    switchPage("customers");
  } catch (err) {
    $("#login-error").textContent = err.message;
  }
}

async function loadCustomers() {
  const q = $("#customer-q").value.trim();
  const qs = q ? `?q=${encodeURIComponent(q)}` : "";
  state.customers = await api(`/api/customers${qs}`);
  const tbody = $("#customer-tbody");
  tbody.innerHTML = state.customers
    .map(
      (c) => `<tr>
      <td>${c.id}</td>
      <td>${escapeHtml(c.name)}</td>
      <td>${escapeHtml(c.phone)}</td>
      <td>${escapeHtml(c.gender || "-")}</td>
      <td>${escapeHtml(c.birthday || "-")}</td>
      <td>${c.visit_count ? `${formatVisitTime(c.last_visited_at)}（${c.visit_count}）` : "尚无回访"}</td>
      <td>${escapeHtml(c.notes || "")}</td>
      <td class="row">
        <button data-visit-customer="${c.id}">回访</button>
        <button data-edit-customer="${c.id}">编辑</button>
        <button data-del-customer="${c.id}">删除</button>
      </td>
    </tr>`
    )
    .join("");
}

function openCustomerDialog(customer) {
  $("#customer-dialog-title").textContent = customer ? "编辑客户" : "新建客户";
  $("#customer-id").value = customer?.id || "";
  $("#c-name").value = customer?.name || "";
  $("#c-phone").value = customer?.phone || "";
  $$('input[name="c-gender"]').forEach((el) => {
    el.checked = el.value === (customer?.gender || "");
  });
  $("#c-birthday").value = customer?.birthday || "";
  $("#c-notes").value = customer?.notes || "";
  $("#customer-dialog").showModal();
}

async function saveCustomer(e) {
  e.preventDefault();
  const id = $("#customer-id").value;
  const genderEl = $$('input[name="c-gender"]').find((el) => el.checked);
  const body = {
    name: $("#c-name").value.trim(),
    phone: $("#c-phone").value.trim(),
    gender: genderEl ? genderEl.value : "",
    birthday: $("#c-birthday").value || null,
    notes: $("#c-notes").value.trim(),
  };
  if (id) await api(`/api/customers/${id}`, { method: "PUT", body: JSON.stringify(body) });
  else await api("/api/customers", { method: "POST", body: JSON.stringify(body) });
  $("#customer-dialog").close();
  await loadCustomers();
}

async function loadProjects() {
  state.projects = await api("/api/projects");
  $("#project-tbody").innerHTML = state.projects
    .map(
      (p) => `<tr>
      <td>${p.id}</td>
      <td>${escapeHtml(p.name)}<div class="muted">${escapeHtml(p.description || "")}</div></td>
      <td>¥${Number(p.price).toFixed(2)}</td>
      <td>${p.graft_count} ${escapeHtml(projectUnit(p.unit))}</td>
      <td><span class="badge ${p.active ? "" : "off"}">${p.active ? "启用" : "停用"}</span></td>
      <td class="row">
        <button data-edit-project="${p.id}">编辑</button>
        ${p.active ? `<button data-off-project="${p.id}">停用</button>` : ""}
      </td>
    </tr>`
    )
    .join("");
}

function openProjectDialog(project) {
  $("#project-dialog-title").textContent = project ? "编辑项目" : "新建项目";
  $("#project-id").value = project?.id || "";
  $("#p-name").value = project?.name || "";
  $("#p-price").value = project?.price ?? "";
  $("#p-graft").value = project?.graft_count ?? 1;
  $("#p-unit").value = projectUnit(project?.unit || (project ? "" : "个"));
  $("#p-desc").value = project?.description || "";
  $("#p-active").checked = project?.active ?? true;
  $("#project-dialog").showModal();
}

async function saveProject(e) {
  e.preventDefault();
  const id = $("#project-id").value;
  const body = {
    name: $("#p-name").value.trim(),
    price: Number($("#p-price").value),
    graft_count: Number($("#p-graft").value || 0),
    unit: projectUnit($("#p-unit").value),
    description: $("#p-desc").value.trim(),
    active: $("#p-active").checked,
  };
  if (id) await api(`/api/projects/${id}`, { method: "PUT", body: JSON.stringify(body) });
  else await api("/api/projects", { method: "POST", body: JSON.stringify(body) });
  $("#project-dialog").close();
  await loadProjects();
}

function isPhysicalUnit(unit) {
  return ["支", "个", "盒"].includes(projectUnit(unit));
}

async function loadInventory() {
  const q = $("#inventory-q").value.trim();
  const qs = q ? `?q=${encodeURIComponent(q)}` : "";
  const list = await api(`/api/inventory${qs}`);
  state.projects = list;
  $("#inventory-tbody").innerHTML = list
    .map((p) => {
      const unit = projectUnit(p.unit);
      const physical = isPhysicalUnit(unit);
      return `<tr>
        <td>${escapeHtml(p.name)}</td>
        <td>${p.graft_count} ${escapeHtml(unit)}</td>
        <td>${physical ? `${p.stock_qty || 0} ${escapeHtml(unit)}` : "—"}</td>
        <td>${physical ? `¥${Number(p.cost_price || 0).toFixed(2)}` : "—"}</td>
        <td>¥${Number(p.price).toFixed(2)}</td>
        <td><button data-stock-project="${p.id}">进出货</button></td>
      </tr>`;
    })
    .join("");
}

async function openStockDialog(project) {
  $("#stock-dialog-title").textContent = project.name;
  $("#stock-project-id").value = project.id;
  $("#s-qty").value = 1;
  $("#s-remark").value = "";
  const unit = projectUnit(project.unit);
  $("#stock-summary").textContent = `库存 ${project.stock_qty || 0} ${unit} · 进货价 ¥${Number(project.cost_price || 0).toFixed(2)}`;
  await loadStockMoves(project.id, unit);
  $("#stock-dialog").showModal();
}

async function loadStockMoves(projectId, unit) {
  const moves = await api(`/api/inventory/movements?project_id=${projectId}`);
  const box = $("#stock-moves");
  if (!moves.length) {
    box.innerHTML = `<div class="muted">暂无进出货记录</div>`;
    return;
  }
  box.innerHTML = moves
    .map((m) => {
      const inbound = m.kind === "IN";
      const cost = Number(m.unit_cost || 0) > 0 ? ` · ¥${Number(m.unit_cost).toFixed(2)}` : "";
      const when = formatVisitTime(m.moved_at || m.created_at).slice(0, 10);
      return `<div class="visit-item">
        <div class="visit-head">
          <time>${inbound ? "入库" : "出货"} ${inbound ? "+" : "-"}${m.quantity} ${escapeHtml(unit || "")}</time>
          <span class="muted">${escapeHtml(when)}</span>
        </div>
        <p>${escapeHtml(m.project_name || "")}${cost}${m.remark ? " · " + escapeHtml(m.remark) : ""}</p>
      </div>`;
    })
    .join("");
}

function todayDate() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

async function openInboundDialog(projectId) {
  const list = await api("/api/inventory");
  state.projects = list;
  if (!list.length) {
    alert("暂无产品，请先在「项目」里添加");
    return;
  }
  $("#in-product").innerHTML = list
    .map((p) => `<option value="${p.id}">${escapeHtml(p.name)}</option>`)
    .join("");
  if (projectId) $("#in-product").value = String(projectId);
  $("#in-date").value = todayDate();
  $("#in-qty").value = 1;
  fillInboundPrice();
  $("#inbound-dialog").showModal();
}

function fillInboundPrice() {
  const p = (state.projects || []).find((x) => String(x.id) === $("#in-product").value);
  $("#in-price").value = p && Number(p.cost_price) > 0 ? Number(p.cost_price) : "";
}

async function saveInbound(e) {
  e.preventDefault();
  const projectId = Number($("#in-product").value);
  const movedAt = $("#in-date").value;
  const unitCost = Number($("#in-price").value);
  const quantity = Number($("#in-qty").value || 0);
  if (!projectId) {
    alert("请选择产品名");
    return;
  }
  if (!movedAt) {
    alert("请选择进货日期");
    return;
  }
  if (Number.isNaN(unitCost) || unitCost < 0) {
    alert("请填写价格");
    return;
  }
  if (quantity < 1) {
    alert("请填写数量");
    return;
  }
  try {
    await api("/api/inventory/in", {
      method: "POST",
      body: JSON.stringify({
        project_id: projectId,
        quantity,
        unit_cost: unitCost,
        moved_at: movedAt,
      }),
    });
    $("#inbound-dialog").close();
    await loadInventory();
  } catch (err) {
    alert(err.message);
  }
}

async function submitStock(kind) {
  const projectId = Number($("#stock-project-id").value);
  const quantity = Number($("#s-qty").value || 0);
  if (!projectId || quantity < 1) {
    alert("请填写数量");
    return;
  }
  try {
    await api("/api/inventory/out", {
      method: "POST",
      body: JSON.stringify({
        project_id: projectId,
        quantity,
        remark: $("#s-remark").value.trim(),
      }),
    });
    await loadInventory();
    const fresh = (state.projects || []).find((p) => p.id === projectId);
    if (fresh) {
      $("#stock-summary").textContent = `库存 ${fresh.stock_qty || 0} ${projectUnit(fresh.unit)} · 进货价 ¥${Number(fresh.cost_price || 0).toFixed(2)}`;
      await loadStockMoves(projectId, projectUnit(fresh.unit));
    }
    $("#s-qty").value = 1;
    $("#s-remark").value = "";
  } catch (err) {
    alert(err.message);
  }
}

async function loadBilling() {
  const [customers, projects] = await Promise.all([
    api("/api/customers"),
    api("/api/projects?active_only=true"),
  ]);
  state.customers = customers;
  state.projects = projects;
  $("#billing-customer").innerHTML = customers
    .map((c) => `<option value="${c.id}">${escapeHtml(c.name)} (${escapeHtml(c.phone)})</option>`)
    .join("");
  $("#billing-projects").innerHTML = projects
    .map(
      (p) => `<label class="check-item">
      <input type="checkbox" data-pid="${p.id}" data-price="${p.price}" />
      <span>${escapeHtml(p.name)} · ¥${Number(p.price).toFixed(2)} · ${p.graft_count} ${escapeHtml(projectUnit(p.unit))}</span>
      <span>数量</span>
      <input type="number" min="1" value="1" data-qty="${p.id}" />
    </label>`
    )
    .join("");
  updateBillingTotal();
}

function updateBillingTotal() {
  let total = 0;
  $$("#billing-projects .check-item").forEach((row) => {
    const cb = row.querySelector('input[type="checkbox"]');
    const qty = Number(row.querySelector('input[type="number"]').value || 1);
    if (cb.checked) total += Number(cb.dataset.price) * qty;
  });
  $("#billing-total").textContent = `合计：¥${total.toFixed(2)}`;
}

async function submitBilling(e) {
  e.preventDefault();
  const items = [];
  $$("#billing-projects .check-item").forEach((row) => {
    const cb = row.querySelector('input[type="checkbox"]');
    const qty = Number(row.querySelector('input[type="number"]').value || 1);
    if (cb.checked) items.push({ project_id: Number(cb.dataset.pid), quantity: qty });
  });
  if (!items.length) {
    alert("请至少选择一个项目");
    return;
  }
  const order = await api("/api/orders", {
    method: "POST",
    body: JSON.stringify({
      customer_id: Number($("#billing-customer").value),
      items,
      remark: $("#billing-remark").value.trim(),
    }),
  });
  alert(`订单已生成：${order.order_no}`);
  switchPage("orders");
}

async function loadOrders() {
  const status = $("#order-status").value;
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  state.orders = await api(`/api/orders${qs}`);
  $("#order-tbody").innerHTML = state.orders
    .map((o) => {
      const detail = (o.items || [])
        .map((i) => `${escapeHtml(i.project_name)} x${i.quantity}`)
        .join("<br/>");
      return `<tr>
        <td>${escapeHtml(o.order_no)}</td>
        <td>${escapeHtml(o.customer_name || "")}<div class="muted">${escapeHtml(o.customer_phone || "")}</div></td>
        <td>¥${Number(o.total_amount).toFixed(2)}</td>
        <td>
          <select data-status-order="${o.id}">
            ${["PENDING", "PAID", "DONE", "CANCELLED"]
              .map((s) => `<option value="${s}" ${s === o.status ? "selected" : ""}>${statusLabel(s)}</option>`)
              .join("")}
          </select>
        </td>
        <td>${formatTime(o.created_at)}</td>
        <td>${detail || "-"}</td>
        <td><button data-save-status="${o.id}">更新状态</button></td>
      </tr>`;
    })
    .join("");
}

function statusLabel(s) {
  return ({ PENDING: "待付款", PAID: "已付款", DONE: "已完成", CANCELLED: "已取消" }[s] || s);
}

function formatTime(v) {
  try {
    return new Date(v).toLocaleString();
  } catch {
    return v;
  }
}

function formatVisitTime(v) {
  if (!v) return "";
  return String(v).replace("T", " ").replace("Z", " ").trim().slice(0, 16);
}

function toDatetimeLocalValue(d = new Date()) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function openVisitDialog(customer) {
  $("#visit-dialog-title").textContent = `回访 · ${customer.name}`;
  $("#visit-customer-id").value = customer.id;
  $("#v-content").value = "";
  $("#v-time").value = toDatetimeLocalValue();
  await loadVisitList(customer.id);
  $("#visit-dialog").showModal();
}

async function loadVisitList(customerId) {
  const visits = await api(`/api/customers/${customerId}/visits`);
  const box = $("#visit-list");
  if (!visits.length) {
    box.innerHTML = `<div class="muted">暂无回访记录</div>`;
    return;
  }
  box.innerHTML = visits
    .map(
      (v) => `<div class="visit-item">
        <div class="visit-head">
          <time>${escapeHtml(formatVisitTime(v.visited_at))}</time>
          <button type="button" data-del-visit="${v.id}">删除</button>
        </div>
        <p>${escapeHtml(v.content || "无内容")}</p>
      </div>`
    )
    .join("");
}

async function saveVisit(e) {
  e.preventDefault();
  const customerId = $("#visit-customer-id").value;
  const local = $("#v-time").value;
  if (!customerId || !local) return;
  try {
    await api(`/api/customers/${customerId}/visits`, {
      method: "POST",
      body: JSON.stringify({
        visited_at: local.length === 16 ? `${local}:00` : local,
        content: $("#v-content").value.trim(),
      }),
    });
    $("#v-content").value = "";
    $("#v-time").value = toDatetimeLocalValue();
    await loadVisitList(customerId);
    await loadCustomers();
  } catch (err) {
    alert(err.message);
  }
}

function escapeHtml(str) {
  return String(str)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

document.addEventListener("click", async (e) => {
  const t = e.target;
  if (!(t instanceof HTMLElement)) return;

  if (t.dataset.page) switchPage(t.dataset.page);
  if (t.id === "logout-btn") logout();
  if (t.id === "account-btn") openAccountDialog();
  if (t.id === "account-cancel") $("#account-dialog").close();
  if (t.id === "customer-search") loadCustomers();
  if (t.id === "customer-new") openCustomerDialog(null);
  if (t.id === "customer-cancel") $("#customer-dialog").close();
  if (t.id === "visit-cancel") $("#visit-dialog").close();
  if (t.id === "project-new") openProjectDialog(null);
  if (t.id === "project-cancel") $("#project-dialog").close();
  if (t.id === "inventory-search") loadInventory();
  if (t.id === "inventory-in") openInboundDialog();
  if (t.id === "inbound-cancel") $("#inbound-dialog").close();
  if (t.id === "stock-cancel") $("#stock-dialog").close();
  if (t.id === "stock-out") submitStock("OUT");
  if (t.id === "order-refresh") loadOrders();

  if (t.dataset.editCustomer) {
    const c = state.customers.find((x) => String(x.id) === t.dataset.editCustomer);
    openCustomerDialog(c);
  }
  if (t.dataset.visitCustomer) {
    const c = state.customers.find((x) => String(x.id) === t.dataset.visitCustomer);
    if (c) openVisitDialog(c);
  }
  if (t.dataset.delVisit) {
    const customerId = $("#visit-customer-id").value;
    if (!confirm("确认删除这条回访？")) return;
    try {
      await api(`/api/customers/${customerId}/visits/${t.dataset.delVisit}`, { method: "DELETE" });
      await loadVisitList(customerId);
      await loadCustomers();
    } catch (err) {
      alert(err.message);
    }
  }
  if (t.dataset.delCustomer) {
    if (!confirm("确认删除该客户？")) return;
    try {
      await api(`/api/customers/${t.dataset.delCustomer}`, { method: "DELETE" });
      await loadCustomers();
    } catch (err) {
      alert(err.message);
    }
  }
  if (t.dataset.editProject) {
    const p = state.projects.find((x) => String(x.id) === t.dataset.editProject);
    openProjectDialog(p);
  }
  if (t.dataset.offProject) {
    await api(`/api/projects/${t.dataset.offProject}`, { method: "DELETE" });
    await loadProjects();
  }
  if (t.dataset.stockProject) {
    const p = (state.projects || []).find((x) => String(x.id) === t.dataset.stockProject);
    if (p) openStockDialog(p);
  }
  if (t.dataset.saveStatus) {
    const select = document.querySelector(`select[data-status-order="${t.dataset.saveStatus}"]`);
    await api(`/api/orders/${t.dataset.saveStatus}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: select.value }),
    });
    await loadOrders();
  }
});

document.addEventListener("input", (e) => {
  if (e.target.closest("#billing-projects")) updateBillingTotal();
});
document.addEventListener("change", (e) => {
  if (e.target.closest("#billing-projects")) updateBillingTotal();
  if (e.target.id === "order-status") loadOrders();
  if (e.target.id === "in-product") fillInboundPrice();
});

async function loadAppDownload() {
  try {
    const info = await fetch("/api/app/info").then((res) => {
      if (!res.ok) throw new Error("no apk");
      return res.json();
    });
    const sizeMb = (info.size_bytes / 1048576).toFixed(1);
    const el = $("#app-download");
    el.innerHTML = `<a href="${info.download_url}">下载 Android 客户端 v${info.version_name}</a>（${sizeMb} MB）`;
    el.classList.remove("hidden");
  } catch (_) {
    /* 安装包未发布时不展示 */
  }
}

async function openAccountDialog() {
  $("#account-error").textContent = "";
  $("#a-current-password").value = "";
  $("#a-new-password").value = "";
  $("#a-confirm-password").value = "";
  try {
    const me = await api("/api/auth/me");
    $("#a-username").value = me.username || "";
  } catch (err) {
    $("#a-username").value = "";
    $("#account-error").textContent = err.message;
  }
  $("#account-dialog").showModal();
}

async function saveAccount(e) {
  e.preventDefault();
  $("#account-error").textContent = "";
  const username = $("#a-username").value.trim();
  const currentPassword = $("#a-current-password").value;
  const newPassword = $("#a-new-password").value;
  const confirmPassword = $("#a-confirm-password").value;
  if (newPassword && newPassword !== confirmPassword) {
    $("#account-error").textContent = "两次输入的新密码不一致";
    return;
  }
  try {
    const data = await api("/api/auth/me", {
      method: "PATCH",
      body: JSON.stringify({
        current_password: currentPassword,
        username,
        new_password: newPassword || null,
      }),
    });
    state.token = data.access_token;
    localStorage.setItem(TOKEN_KEY, state.token);
    $("#account-dialog").close();
  } catch (err) {
    $("#account-error").textContent = err.message;
  }
}

$("#login-form").addEventListener("submit", login);
$("#customer-form").addEventListener("submit", saveCustomer);
$("#visit-form").addEventListener("submit", saveVisit);
$("#project-form").addEventListener("submit", saveProject);
$("#billing-form").addEventListener("submit", submitBilling);
$("#inbound-form").addEventListener("submit", saveInbound);
$("#account-form").addEventListener("submit", saveAccount);

loadAppDownload();
if (state.token) {
  showApp(true);
  switchPage("customers");
} else {
  showApp(false);
}
