const state={page:"dashboard",participants:[],textbooks:[],codes:[],orders:[],operations:[],documents:[],history:[]};
const $=id=>document.getElementById(id);
async function api(url,options={}){
  const r=await fetch(url,{headers:{"Content-Type":"application/json",...(options.headers||{})},...options});
  const text=await r.text(); let d={}; try{d=text?JSON.parse(text):{}}catch{}
  if(!r.ok)throw new Error(d.message||"Ошибка API");
  return d;
}
const esc=v=>String(v??"").replace(/[&<>"]/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[m]));
function showError(message){$("error").textContent=message;$("error").classList.remove("hidden")}
function clearError(){$("error").classList.add("hidden")}
function formSubmit(id,fn){$(id).addEventListener("submit",async e=>{e.preventDefault();clearError();try{await fn(new FormData(e.target));e.target.reset();await loadPage()}catch(x){showError(x.message)}})}
function options(items,valueField="id",labelField="name"){return items.map(x=>`<option value="${esc(x[valueField])}">${esc(x[labelField]||x.title||x.gtin||x.id)}</option>`).join("")}
function panel(title,body){return `<section class="panel"><div class="panel-head"><h2>${title}</h2></div>${body}</section>`}
function empty(text){return `<div class="empty">${text}</div>`}
function table(headers,rows){return `<div class="table-wrap"><table><thead><tr>${headers.map(h=>`<th>${h}</th>`).join("")}</tr></thead><tbody>${rows||""}</tbody></table>${rows?"":empty("Данных пока нет")}</div>`}
function status(v){return `<span class="status">${esc(v)}</span>`}
async function refreshData(){
  const [p,t,c,o,op,d,h]=await Promise.all([
    api("/api/participants"),api("/api/textbooks"),api("/api/marking-codes"),api("/api/code-orders"),
    api("/api/operations"),api("/api/documents"),api("/api/history")
  ]);
  Object.assign(state,{participants:p,textbooks:t,codes:c,orders:o,operations:op,documents:d,history:h});
}
function dashboard(){
  const cards=[
    ["Учебники",state.textbooks.length],["Коды маркировки",state.codes.length],
    ["Заказы КМ",state.orders.length],["Участники",state.participants.length]
  ];
  const rows=state.textbooks.slice(-10).reverse().map(x=>`<tr><td>${esc(x.title)}</td><td>${esc(x.gtin)}</td><td>${esc(x.author)}</td><td>${status(x.status)}</td></tr>`).join("");
  $("content").innerHTML=`<div class="cards">${cards.map(c=>`<div class="card"><span>${c[0]}</span><strong>${c[1]}</strong></div>`).join("")}</div>
  ${panel("Последние учебники",table(["Название","GTIN","Автор","Статус"],rows))}`;
}
function participants(){
  const rows=state.participants.map(x=>`<tr><td>${esc(x.name)}</td><td>${esc(x.inn)}</td><td>${esc(x.phone)}</td><td>${status(x.status)}</td><td><button class="small" onclick="deposit('${x.id}')">Пополнить</button></td></tr>`).join("");
  $("content").innerHTML=`<div class="grid2">${panel("Новый участник",`<form id="participantForm" class="form">
  <input name="inn" placeholder="ИНН *" required><input name="name" placeholder="Наименование *" required>
  <input name="legalForm" placeholder="Организационно-правовая форма"><input name="country" placeholder="Страна">
  <input name="legalAddress" placeholder="Юридический адрес"><input name="actualAddress" placeholder="Фактический адрес">
  <input name="phone" placeholder="Телефон"><input name="email" type="email" placeholder="Email">
  <button class="primary">Создать участника</button></form>`)}
  ${panel("Участники",table(["Наименование","ИНН","Телефон","Статус",""],rows))}</div>`;
  formSubmit("participantForm",async f=>{await api("/api/participants",{method:"POST",body:JSON.stringify(Object.fromEntries(f.entries()))})});
}
function textbooks(){
  const rows=state.textbooks.map(x=>`<tr><td>${esc(x.title)}</td><td>${esc(x.gtin)}</td><td>${esc(x.author)}</td><td>${esc(x.publisher)}</td><td>${esc(x.year)}</td><td>${status(x.status)}</td><td>${x.status==="DRAFT"?`<button class="small" onclick="publishTextbook('${x.id}')">Опубликовать</button>`:""}</td></tr>`).join("");
  $("content").innerHTML=`<div class="grid2">${panel("Новая карточка учебника",`<form id="textbookForm" class="form">
  <select name="participantId"><option value="">Участник – необязательно</option>${options(state.participants)}</select>
  <input name="gtin" placeholder="GTIN *" required><input name="title" placeholder="Название *" required>
  <input name="fullTitle" placeholder="Полное название"><input name="author" placeholder="Автор *" required>
  <input name="schoolClass" placeholder="Класс *" required><input name="subject" placeholder="Предмет *" required>
  <input name="publisher" placeholder="Издательство *" required><input name="year" type="number" placeholder="Год издания *" required>
  <input name="language" placeholder="Язык *" required><input name="isbn" placeholder="ISBN *" required>
  <input name="printRun" type="number" placeholder="Тираж"><input name="ageCategory" placeholder="Возрастная категория">
  <input name="countryOfProduction" placeholder="Страна производства"><input name="manufacturer" placeholder="Производитель">
  <textarea name="description" placeholder="Описание"></textarea><button class="primary">Создать карточку</button></form>`)}
  ${panel("Карточки учебников",table(["Название","GTIN","Автор","Издательство","Год","Статус",""],rows))}</div>`;
  formSubmit("textbookForm",async f=>{const o=Object.fromEntries(f.entries());if(!o.participantId)delete o.participantId;for(const k of ["year","printRun"])if(o[k])o[k]=Number(o[k]);else delete o[k];await api("/api/textbooks",{method:"POST",body:JSON.stringify(o)})});
}
async function publishTextbook(id){try{await api("/api/textbooks/"+id+"/publish",{method:"POST"});await loadPage()}catch(e){showError(e.message)}}
async function deposit(id){const amount=prompt("Сумма пополнения, KGS:");if(!amount)return;try{await api("/api/participants/"+id+"/balance?amount="+encodeURIComponent(amount),{method:"POST"});await loadPage()}catch(e){showError(e.message)}}
function codes(){
  const rows=state.codes.map(x=>`<tr><td>${esc(x.serial)}</td><td>${esc(x.gtin)}</td><td>${status(x.status)}</td><td>${x.status==="EMITTED"?`<button class="small" onclick="changeCode('${x.id}','apply')">Нанести</button>`:""} ${x.status==="APPLIED"?`<button class="small" onclick="changeCode('${x.id}','circulate')">Ввести в оборот</button>`:""} ${x.status==="IN_CIRCULATION"?`<button class="small danger" onclick="changeCode('${x.id}','withdraw')">Вывести</button>`:""}</td></tr>`).join("");
  $("content").innerHTML=`<div class="grid2">${panel("Генерация кодов",`<form id="codeForm" class="form">
  <select name="participantId" required><option value="">Участник *</option>${options(state.participants)}</select>
  <select name="textbookId" required><option value="">Учебник *</option>${options(state.textbooks,"id","title")}</select>
  <input name="quantity" type="number" min="1" value="1" placeholder="Количество *" required>
  <button class="primary">Сгенерировать КМ</button></form>`)}
  ${panel("Коды маркировки",table(["Серийный номер","GTIN","Статус","Действия"],rows))}</div>`;
  formSubmit("codeForm",async f=>{const q=Object.fromEntries(f.entries());await api("/api/marking-codes/generate?participantId="+q.participantId+"&textbookId="+q.textbookId+"&quantity="+q.quantity,{method:"POST"})});
}
async function changeCode(id,action){try{await api("/api/marking-codes/"+id+"/"+action,{method:"POST"});await loadPage()}catch(e){showError(e.message)}}
function orders(){
  const rows=state.orders.map(x=>`<tr><td>${esc(x.id)}</td><td>${esc(x.gtin)}</td><td>${x.quantity}</td><td>${x.amount} KGS</td><td>${status(x.status)}</td></tr>`).join("");
  $("content").innerHTML=`<div class="grid2">${panel("Новый заказ КМ",`<form id="orderForm" class="form"><select name="participantId" required><option value="">Участник *</option>${options(state.participants)}</select><select name="textbookId" required><option value="">Учебник *</option>${options(state.textbooks,"id","title")}</select><input name="quantity" type="number" min="1" value="1" required><button class="primary">Создать заказ</button></form>`)}${panel("Заказы",table(["ID","GTIN","Количество","Сумма","Статус"],rows))}</div>`;
  formSubmit("orderForm",async f=>{const q=Object.fromEntries(f.entries());await api("/api/code-orders",{method:"POST",body:JSON.stringify({participantId:q.participantId,textbookId:q.textbookId,quantity:Number(q.quantity)})})});
}
function operations(){
  const rows=state.operations.map(x=>`<tr><td>${esc(x.type)}</td><td>${x.quantity}</td><td>${status(x.status)}</td><td>${esc(x.reason)}</td><td>${esc(x.createdAt)}</td></tr>`).join("");
  $("content").innerHTML=`<div class="grid2">${panel("Операция над кодами",`<form id="opForm" class="form"><select name="participantId" required><option value="">Участник *</option>${options(state.participants)}</select><select name="textbookId"><option value="">Учебник</option>${options(state.textbooks,"id","title")}</select><select name="type" required><option value="marking">Нанесение</option><option value="introduction">Ввод в оборот</option><option value="withdrawal">Вывод из оборота</option></select><textarea name="codeIds" placeholder="ID кодов через запятую" required></textarea><input name="reason" placeholder="Причина (для вывода)"><button class="primary">Выполнить операцию</button></form>`)}${panel("История операций",table(["Тип","Количество","Статус","Причина","Дата"],rows))}</div>`;
  formSubmit("opForm",async f=>{const q=Object.fromEntries(f.entries());const ids=q.codeIds.split(",").map(x=>x.trim()).filter(Boolean);await api("/api/operations/"+q.type,{method:"POST",body:JSON.stringify({participantId:q.participantId,textbookId:q.textbookId||null,codeIds:ids,reason:q.reason||null})})});
}
function documents(){const rows=state.documents.map(x=>`<tr><td>${esc(x.number)}</td><td>${esc(x.type)}</td><td>${status(x.status)}</td><td>${esc(x.createdAt)}</td></tr>`).join("");$("content").innerHTML=panel("Документы",table(["Номер","Тип","Статус","Дата"],rows))}
async function finance(){
  const rows=await Promise.all(state.participants.map(async p=>{try{const b=await api("/api/billing/balance/"+p.id);return `<tr><td>${esc(p.name)}</td><td>${b.balance} KGS</td><td>${b.reservedBalance} KGS</td><td>${b.availableBalance} KGS</td><td><button class="small" onclick="deposit('${p.id}')">Пополнить</button></td></tr>`}catch{return ""}}));
  $("content").innerHTML=panel("Финансы участников",table(["Участник","Баланс","Зарезервировано","Доступно",""],rows.join("")));
}
function history(){const rows=state.history.map(x=>`<tr><td>${esc(x.operation)}</td><td>${esc(x.oldStatus)}</td><td>${esc(x.newStatus)}</td><td>${esc(x.codeId)}</td><td>${esc(x.createdAt)}</td></tr>`).join("");$("content").innerHTML=panel("История изменений кодов",table(["Операция","Было","Стало","Код","Дата"],rows))}
function settings(){$("content").innerHTML=panel("Настройки",`<div class="settings"><div><b>Продуктовая группа</b><p>Учебники</p></div><div><b>Хранилище</b><p>In-Memory MVP</p></div><div><b>Стоимость КМ</b><p>1,00 KGS за код</p></div><div><b>Система</b><p>Java 21 + Spring Boot</p></div></div>`)}

const pages={dashboard:["АИС маркировки учебников","Рабочая панель MVP",dashboard],participants:["Участники","Управление участниками оборота",participants],textbooks:["Учебники","Карточки учебников",textbooks],codes:["Коды маркировки","Генерация и жизненный цикл КМ",codes],orders:["Заказы КМ","Заказы и стоимость кодов",orders],operations:["Операции","Нанесение, ввод и вывод из оборота",operations],documents:["Документы","Документы операций",documents],finance:["Финансы","Баланс и резервирование",finance],history:["История","Аудит изменений",history],settings:["Настройки","Параметры MVP",settings]};
async function loadPage(){
  clearError();await refreshData();
  const [title,subtitle,render]=pages[state.page];$("pageTitle").textContent=title;$("pageSubtitle").textContent=subtitle;
  await render();
}
document.querySelectorAll("#nav button").forEach(b=>b.addEventListener("click",async()=>{document.querySelectorAll("#nav button").forEach(x=>x.classList.remove("active"));b.classList.add("active");state.page=b.dataset.page;await loadPage()}));
$("refresh").addEventListener("click",loadPage);
loadPage().catch(e=>showError(e.message));