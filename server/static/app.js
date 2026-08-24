const TOKEN_KEY = "hair_clinic_token";

const state = {
  token: localStorage.getItem(TOKEN_KEY) || "",
  customers: [],
  projects: [],
  orders: [],
};

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

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
      <td>${escapeHtml(c.notes || "")}</td>
      <td class="row">
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
      <td>${p.graft_count}</td>
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
  $("#p-graft").value = project?.graft_count ?? 0;
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
    description: $("#p-desc").value.trim(),
    active: $("#p-active").checked,
  };
  if (id) await api(`/api/projects/${id}`, { method: "PUT", body: JSON.stringify(body) });
  else await api("/api/projects", { method: "POST", body: JSON.stringify(body) });
  $("#project-dialog").close();
  await loadProjects();
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
      <span>${escapeHtml(p.name)} · ¥${Number(p.price).toFixed(2)} · ${p.graft_count}单位</span>
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
  if (t.id === "customer-search") loadCustomers();
  if (t.id === "customer-new") openCustomerDialog(null);
  if (t.id === "customer-cancel") $("#customer-dialog").close();
  if (t.id === "project-new") openProjectDialog(null);
  if (t.id === "project-cancel") $("#project-dialog").close();
  if (t.id === "order-refresh") loadOrders();

  if (t.dataset.editCustomer) {
    const c = state.customers.find((x) => String(x.id) === t.dataset.editCustomer);
    openCustomerDialog(c);
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

$("#login-form").addEventListener("submit", login);
$("#customer-form").addEventListener("submit", saveCustomer);
$("#project-form").addEventListener("submit", saveProject);
$("#billing-form").addEventListener("submit", submitBilling);

loadAppDownload();
if (state.token) {
  showApp(true);
  switchPage("customers");
} else {
  showApp(false);
}
