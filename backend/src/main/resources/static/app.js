let state = {
    page: "dashboard",
    participants: [],
    textbooks: [],
    codes: [],
    orders: [],
    operations: [],
    documents: [],
    history: [],
    users: [],
    me: null
};
const token = () => localStorage.getItem("ais_token");
const $ = id => document.getElementById(id);

async function api(url, options = {}) {
    const headers = {"Content-Type": "application/json", ...(options.headers || {})};
    if (token()) headers.Authorization = "Bearer " + token();
    const r = await fetch(url, {...options, headers});
    const text = await r.text();
    let d = {};
    try {
        d = text ? JSON.parse(text) : {}
    } catch {
    }
    if (!r.ok) {
        if (r.status === 401) {
            localStorage.removeItem("ais_token");
            showLogin();
        }
        throw new Error(d.message || "Ошибка API")
    }
    return d;
}

function esc(v) {
    return String(v ?? "").replace(/[&<>"]/g, m => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;"}[m]))
}

function status(v) {
    return `<span class="status">${esc(v)}</span>`
}

function showLogin() {
    document.body.innerHTML = `<div class="login"><div class="login-card"><div class="logo dark">ТЕКШЕР <span>AIS</span></div><h1>Вход в AIS</h1><p>АИС маркировки учебников</p><form id="loginForm" class="form one"><input name="login" placeholder="Логин" required><input name="password" type="password" placeholder="Пароль" required><button class="primary">Войти</button></form><div id="loginError" class="login-error"></div><div class="demo">Администратор: <b>admin / admin</b><br>Пользователь: <b>user / user</b></div></div></div>`;
    $("loginForm").addEventListener("submit", async e => {
        e.preventDefault();
        $("loginError").textContent = "";
        try {
            const r = await fetch("/api/auth/login", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(Object.fromEntries(new FormData(e.target).entries()))
            });
            const d = await r.json();
            if (!r.ok) throw new Error(d.message || "Ошибка входа");
            localStorage.setItem("ais_token", d.token);
            location.reload()
        } catch (x) {
            $("loginError").textContent = x.message
        }
    });
}

async function refreshData() {
    const requests = [api("/api/textbooks"), api("/api/marking-codes"), api("/api/code-orders"), api("/api/operations"), api("/api/documents"), api("/api/history")];
    if (state.me?.role === "ADMIN") requests.unshift(api("/api/participants"));
    const data = await Promise.all(requests);
    let p = [], t, c, o, op, d, h;
    if (state.me?.role === "ADMIN") {
        [p, t, c, o, op, d, h] = data
    } else {
        [t, c, o, op, d, h] = data;
    }
    if (state.me?.role === "USER") && state.me.participantId) {
        try {
            p = [await api("/api/participants/" + state.me.participantId)]
        } catch {
        }
    }
    Object.assign(state, {
        participants: p,
        textbooks: t,
        codes: c,
        orders: o,
        operations: op,
        documents: d,
        history: h
    });
    if (state.me?.role === "ADMIN") state.users = await api("/api/users");
}

function showError(message) {
    $("error").textContent = message;
    $("error").classList.remove("hidden")
}

function clearError() {
    $("error").classList.add("hidden")
}

function panel(title, body) {
    return `<section class="panel"><div class="panel-head"><h2>${title}</h2></div>${body}</section>`
}

function empty(t) {
    return `<div class="empty">${t}</div>`
}

function table(headers, rows) {
    return `<div class="table-wrap"><table><thead><tr>${headers.map(h => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows || ""}</tbody></table>${rows ? "" : empty("Данных пока нет")}</div>`
}

function opts(items, label = "name") {
    return items.map(x => `<option value="${esc(x.id)}">${esc(x[label] || x.title || x.gtin)}</option>`).join("")
}

function formSubmit(id, fn) {
    $(id).addEventListener("submit", async e => {
        e.preventDefault();
        clearError();
        try {
            await fn(new FormData(e.target));
            e.target.reset();
            await loadPage()
        } catch (x) {
            showError(x.message)
        }
    })
}

function dashboard() {
    const cards = [["Учебники", state.textbooks.length], ["Коды маркировки", state.codes.length], ["Заказы КМ", state.orders.length], ["Участники", state.participants.length]];
    const rows = state.textbooks.slice(-10).reverse().map(x => `<tr><td>${esc(x.title)}</td><td>${esc(x.gtin)}</td><td>${esc(x.author)}</td><td>${status(x.status)}</td></tr>`).join("");
    $("content").innerHTML = `<div class="cards">${cards.map(x => `<div class="card"><span>${x[0]}</span><strong>${x[1]}</strong></div>`).join("")}</div>${panel("Последние учебники", table(["Название", "GTIN", "Автор", "Статус"], rows))}`
}

function participants() {
    if (state.me.role !== "ADMIN") {
        $("content").innerHTML = panel("Участники", empty("Раздел доступен только администратору"));
        return;
    }
    const rows = state.participants.map(x => `<tr><td>${esc(x.name)}</td><td>${esc(x.inn)}</td><td>${esc(x.phone)}</td><td>${status(x.status)}</td><td>${state.me.role === "ADMIN" ? `<button class="small" onclick="deposit('${x.id}')">Пополнить</button>` : ""}</td></tr>`).join("");
    $("content").innerHTML = panel("Участники", table(["Наименование", "ИНН", "Телефон", "Статус", ""], rows)) + (state.me.role === "ADMIN" ? panel("Создать участника", `<form id="participantForm" class="form"><input name="inn" placeholder="ИНН *" required><input name="name" placeholder="Наименование *" required><input name="legalForm" placeholder="Организационно-правовая форма"><input name="country" placeholder="Страна"><input name="legalAddress" placeholder="Юридический адрес"><input name="actualAddress" placeholder="Фактический адрес"><input name="phone" placeholder="Телефон"><input name="email" type="email" placeholder="Email"><button class="primary">Создать участника</button></form>`) : "");
    if (state.me.role === "ADMIN") formSubmit("participantForm", async f => api("/api/participants", {
        method: "POST",
        body: JSON.stringify(Object.fromEntries(f.entries()))
    }))
}

function textbooks() {
    const rows = state.textbooks.map(x => `<tr><td>${esc(x.title)}</td><td>${esc(x.gtin)}</td><td>${esc(x.author)}</td><td>${esc(x.publisher)}</td><td>${esc(x.year)}</td><td>${status(x.status)}</td><td>${x.status === "DRAFT" && (state.me.role === "ADMIN" || state.me.role === "USER") ? `<button class="small" onclick="publishTextbook('${x.id}')">Опубликовать</button>` : ""}</td></tr>`).join("");
    let form = state.me.role === "ADMIN" || state.me.role === "USER" ? `<form id="textbookForm" class="form"><input type="hidden" name="participantId" value="${state.me.role === "USER" ? (state.me.participantId || "") : ""}">${state.me.role === "ADMIN" ? `<select name="participantId"><option value="">Участник – необязательно</option>${opts(state.participants)}</select>` : ""}<input name="gtin" placeholder="GTIN *" required><input name="title" placeholder="Название *" required><input name="fullTitle" placeholder="Полное название"><input name="author" placeholder="Автор *" required><input name="schoolClass" placeholder="Класс *" required><input name="subject" placeholder="Предмет *" required><input name="publisher" placeholder="Издательство *" required><input name="year" type="number" placeholder="Год издания *" required><input name="language" placeholder="Язык *" required><input name="isbn" placeholder="ISBN *" required><input name="printRun" type="number" placeholder="Тираж"><input name="ageCategory" placeholder="Возрастная категория"><input name="countryOfProduction" placeholder="Страна производства"><input name="manufacturer" placeholder="Производитель"><textarea name="description" placeholder="Описание"></textarea><button class="primary">Создать карточку</button></form>` : empty("Создание карточек недоступно для этой роли");
    $("content").innerHTML = `<div class="grid2">${panel("Новая карточка учебника", form)}${panel("Карточки учебников", table(["Название", "GTIN", "Автор", "Издательство", "Год", "Статус", ""], rows))}</div>`;
    if ($("textbookForm")) formSubmit("textbookForm", async f => {
        let o = Object.fromEntries(f.entries());
        if (!o.participantId) delete o.participantId;
        ["year", "printRun"].forEach(k => {
            if (o[k]) o[k] = Number(o[k]); else delete o[k]
        });
        await api("/api/textbooks", {method: "POST", body: JSON.stringify(o)})
    })
}

async function publishTextbook(id) {
    try {
        await api("/api/textbooks/" + id + "/publish", {method: "POST"});
        await loadPage()
    } catch (e) {
        showError(e.message)
    }
}

async function deposit(id) {
    const amount = prompt("Сумма пополнения, KGS:");
    if (amount) try {
        await api("/api/billing/deposit/" + id + "?amount=" + encodeURIComponent(amount), {method: "POST"});
        await loadPage()
    } catch (e) {
        showError(e.message)
    }
}

function codes() {
    const rows = state.codes.map(x => `<tr><td>${esc(x.serial)}</td><td>${esc(x.gtin)}</td><td>${status(x.status)}</td><td><button class="small" onclick="viewCode('${x.id}')">DataMatrix</button>${x.status === "EMITTED" ? ` <button class="small" onclick="changeCode('${x.id}','apply')">Нанести / печать</button>` : ""}${x.status === "APPLIED" ? ` <button class="small" onclick="changeCode('${x.id}','circulate')">Ввести в оборот</button>` : ""}${x.status === "IN_CIRCULATION" ? ` <button class="small danger" onclick="changeCode('${x.id}','withdraw')">Вывести</button>` : ""}</td></tr>`).join("");
    let form = state.me.role === "ADMIN" || state.me.role === "USER" ? `<form id="codeForm" class="form"><input type="hidden" name="participantId" value="${state.me.role === "USER" ? (state.me.participantId || "") : ""}">${state.me.role === "ADMIN" ? `<select name="participantId" required><option value="">Участник *</option>${opts(state.participants)}</select>` : ""}<select name="textbookId" required><option value="">Учебник *</option>${opts(state.textbooks, "title")}</select><input name="quantity" type="number" min="1" value="1" required><button class="primary">Сгенерировать КМ</button></form>` : empty("Генерация КМ недоступна для этой роли");
    $("content").innerHTML = `<div class="grid2">${panel("Формирование КМ", form)}${panel("Коды маркировки", table(["Серийный номер", "GTIN", "Статус", "Действия"], rows))}</div>`;
    if ($("codeForm")) formSubmit("codeForm", async f => {
        const q = Object.fromEntries(f.entries());
        await api("/api/marking-codes/generate?participantId=" + q.participantId + "&textbookId=" + q.textbookId + "&quantity=" + q.quantity, {method: "POST"})
    })
}

function viewCode(id) {
    const c = state.codes.find(x => x.id === id);
    if (!c) return;
    const w = window.open("", "_blank", "width=500,height=600");
    w.document.write(`<html><head><title>DataMatrix ${esc(c.serial)}</title><style>body{text-align:center;font-family:Arial;padding:30px}img{width:280px;height:280px;image-rendering:auto}.meta{margin-top:20px}</style></head><body><h2>Код маркировки</h2><img src="data:image/png;base64,${c.dataMatrixBase64}"><div class="meta">GTIN: ${esc(c.gtin)}<br>Серийный номер: ${esc(c.serial)}<br>GS1: ${esc(c.payload)}</div><button onclick="window.print()">Печать</button></body></html>`);
    w.document.close()
}

async function changeCode(id, a) {
    try {
        await api("/api/marking-codes/" + id + "/" + a, {method: "POST"});
        await loadPage()
    } catch (e) {
        showError(e.message)
    }
}

function orders() {
    const rows = state.orders.map(x => `<tr><td>${esc(x.id)}</td><td>${esc(x.gtin)}</td><td>${x.quantity}</td><td>${x.amount} KGS</td><td>${status(x.status)}</td></tr>`).join("");
    const form = state.me.role === "ADMIN" || state.me.role === "USER" ? `<form id="orderForm" class="form"><input type="hidden" name="participantId" value="${state.me.role === "USER" ? (state.me.participantId || "") : ""}">${state.me.role === "ADMIN" ? `<select name="participantId" required><option value="">Участник *</option>${opts(state.participants)}</select>` : ""}<select name="textbookId" required><option value="">Учебник *</option>${opts(state.textbooks, "title")}</select><input name="quantity" type="number" min="1" value="1" required><button class="primary">Создать заказ КМ</button></form>` : empty("Создание заказов недоступно для этой роли");
    $("content").innerHTML = `<div class="grid2">${panel("Новый заказ КМ", form)}${panel("Заказы", table(["ID", "GTIN", "Количество", "Сумма", "Статус"], rows))}</div>`;
    if ($("orderForm")) formSubmit("orderForm", async f => {
        const q = Object.fromEntries(f.entries());
        await api("/api/code-orders", {
            method: "POST",
            body: JSON.stringify({
                participantId: q.participantId,
                textbookId: q.textbookId,
                quantity: Number(q.quantity)
            })
        })
    })
}

function operations() {
    const rows = state.operations.map(x => `<tr><td>${esc(x.type)}</td><td>${x.quantity}</td><td>${status(x.status)}</td><td>${esc(x.reason)}</td><td>${esc(x.billingId || "")}</td><td>${esc(x.createdAt)}</td></tr>`).join("");
    const available = state.codes.filter(c => c.status === "EMITTED" || c.status === "APPLIED" || c.status === "IN_CIRCULATION");
    const form = `<form id="operationForm" class="form">
      <select name="type" required>
        <option value="MARKING">Нанесение</option>
        <option value="INTRODUCTION">Ввод в оборот</option>
        <option value="WITHDRAWAL">Вывод из оборота</option>
      </select>
      <select name="codeId" required><option value="">Код маркировки *</option>${available.map(c => `<option value="${esc(c.id)}">${esc(c.gtin)} / ${esc(c.serial)} – ${esc(c.status)}</option>`).join("")}</select>
      <input name="reason" placeholder="Причина – для вывода из оборота">
      <button class="primary">Выполнить операцию</button>
    </form>`;
    $("content").innerHTML = `<div class="grid2">${panel("Новая операция", form)}${panel("История операций", table(["Тип","Количество","Статус","Причина","Billing ID","Дата"], rows))}</div>`;
    formSubmit("operationForm", async f => {
        const q = Object.fromEntries(f.entries());
        const code = state.codes.find(x => x.id === q.codeId);
        if (!code) throw new Error("Код не найден");
        const endpoint = {MARKING:"marking",INTRODUCTION:"introduction",WITHDRAWAL:"withdrawal"}[q.type];
        await api("/api/operations/" + endpoint, {
            method:"POST",
            body:JSON.stringify({
                participantId: code.participantId,
                textbookId: code.textbookId,
                codeIds:[q.codeId],
                reason:q.reason || null
            })
        });
    });
}

function documents() {
    const rows = state.documents.map(x => `<tr><td>${esc(x.number)}</td><td>${esc(x.type)}</td><td>${status(x.status)}</td><td>${esc(x.operationId || "")}</td><td>${esc(x.billingId || "")}</td><td>${esc(x.createdAt)}</td></tr>`).join("");
    $("content").innerHTML = panel("Документы", table(["Номер","Тип","Статус","Операция","Billing ID","Дата"], rows));
}

async function finance() {
    if (state.me.role === "ADMIN") {
        const rows = await Promise.all(state.participants.map(async p => {
            try {
                const b = await api("/api/billing/balance/" + p.id);
                return `<tr><td><b>${esc(p.name)}</b><br><small>${esc(p.inn)}</small></td><td>${b.balance} KGS</td><td>${b.reservedBalance} KGS</td><td>${b.availableBalance} KGS</td><td><button class="small" onclick="openFinance('${p.id}')">Открыть</button> <button class="small" onclick="deposit('${p.id}')">Пополнить</button></td></tr>`;
            } catch { return ""; }
        }));
        $("content").innerHTML = panel("Финансы участников", table(["Участник","Баланс","Зарезервировано","Доступно","Действия"], rows.join("")));
        return;
    }
    if (!state.me.participantId) {
        $("content").innerHTML = panel("Финансы", empty("Аккаунт не привязан к участнику"));
        return;
    }
    await openFinance(state.me.participantId);
}

async function openFinance(id) {
    try {
        const p = state.participants.find(x => x.id === id) || await api("/api/participants/" + id);
        const b = await api("/api/billing/balance/" + id);
        const h = await api("/api/billing/history/" + id);
        const rows = h.map(x => `<tr><td>${esc(x.type)}</td><td>${x.amount} KGS</td><td>${status(x.status)}</td><td>${esc(x.id)}</td><td>${esc(x.createdAt)}</td></tr>`).join("");
        const actions = state.me.role === "ADMIN" ? `<div class="settings">
          <div><b>Пополнение счёта</b><p><button class="small" onclick="deposit('${id}')">Пополнить</button></p></div>
          <div><b>Финансовые операции</b><p>Резервирование и списание выполняются автоматически при операциях AIS.</p></div>
        </div>` : "";
        $("content").innerHTML = `<button class="small" onclick="loadPage()">← К списку участников</button><br><br>
          ${panel("Финансовый счёт – " + esc(p.name), `
            <div class="cards">
              <div class="card"><span>Баланс</span><strong>${b.balance} KGS</strong></div>
              <div class="card"><span>Зарезервировано</span><strong>${b.reservedBalance} KGS</strong></div>
              <div class="card"><span>Доступно</span><strong>${b.availableBalance} KGS</strong></div>
            </div>${actions}
          `)}
          <br>${panel("История финансовых операций", table(["Операция","Сумма","Статус","Billing ID","Дата"], rows))}`;
    } catch (e) { showError(e.message); }
}
function history() {
    const rows = state.history.map(x => `<tr><td>${esc(x.operation)}</td><td>${esc(x.oldStatus)}</td><td>${esc(x.newStatus)}</td><td>${esc(x.codeId)}</td><td>${esc(x.createdAt)}</td></tr>`).join("");
    $("content").innerHTML = panel("История изменений кодов", table(["Операция", "Было", "Стало", "Код", "Дата"], rows))
}

function settings() {
    let users = "";
    if (state.me.role === "ADMIN") {
        const rows = state.users.map(u => `<tr><td>${esc(u.fullName)}</td><td>${esc(u.login)}</td><td>${status(u.role)}</td><td>${status(u.status)}</td></tr>`).join("");
        users = panel("Пользователи и роли",
            `<form id="userForm" class="form">
        <input name="fullName" placeholder="ФИО *" required>
        <input name="login" placeholder="Логин *" required>
        <input name="password" type="password" placeholder="Пароль *" required>
        <select name="role"><option value="USER">Пользователь</option><option value="ADMIN">Администратор</option></select>
        <select name="participantId"><option value="">Привязать к участнику</option>${opts(state.participants)}</select>
        <button class="primary">Создать пользователя</button>
      </form>` + table(["ФИО", "Логин", "Роль", "Статус"], rows)
        );
    }
    $("content").innerHTML =
        panel("Моя сессия", `<div class="session"><b>${esc(state.me.fullName)}</b><span>${status(state.me.role)}</span><button class="small danger" onclick="logout()">Выйти</button></div>`) +
        users +
        panel("Параметры MVP", `<div class="settings">
      <div><b>Продуктовая группа</b><p>Учебники</p></div>
      <div><b>Хранилище</b><p>In-Memory MVP</p></div>
      <div><b>Стоимость КМ</b><p>1,00 KGS за код</p></div>
      <div><b>DataMatrix</b><p>GS1 DataMatrix ECC200</p></div>
    </div>`);
    if ($("userForm")) {
        formSubmit("userForm", async f => {
            const o = Object.fromEntries(f.entries());
            if (!o.participantId) delete o.participantId;
            await api("/api/users", {method: "POST", body: JSON.stringify(o)});
        });
    }
}

function logout() {
    localStorage.removeItem("ais_token");
    location.reload()
}

const pages = {
    dashboard: ["АИС маркировки учебников", "Рабочая панель MVP", dashboard],
    participants: ["Участники", "Управление участниками оборота", participants],
    textbooks: ["Учебники", "Карточки учебников", textbooks],
    codes: ["Коды маркировки", "Формирование, нанесение и печать DataMatrix", codes],
    orders: ["Заказы КМ", "Заказ и стоимость кодов", orders],
    operations: ["Операции", "Нанесение, ввод и вывод из оборота", operations],
    documents: ["Документы", "Документы операций", documents],
    finance: ["Финансы", "Баланс и резервирование", finance],
    history: ["История", "Аудит изменений", history],
    settings: ["Настройки", "Пользователи, роли и параметры", settings]
};

async function loadPage() {
    clearError();
    await refreshData();
    const [title, subtitle, render] = pages[state.page];
    $("pageTitle").textContent = title;
    $("pageSubtitle").textContent = subtitle;
    await render()
}

async function boot() {
    try {
        state.me = await api("/api/auth/me");
        document.querySelectorAll("#nav button").forEach(b => b.addEventListener("click", async () => {
            document.querySelectorAll("#nav button").forEach(x => x.classList.remove("active"));
            b.classList.add("active");
            state.page = b.dataset.page;
            await loadPage()
        }));
        $("refresh").addEventListener("click", loadPage);
        await loadPage()
    } catch (e) {
        if (!token()) showLogin(); else showError(e.message)
    }
}

boot();