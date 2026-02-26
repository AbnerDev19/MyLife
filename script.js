
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.8.0/firebase-app.js";
import { getFirestore, doc, getDoc, setDoc } from "https://www.gstatic.com/firebasejs/10.8.0/firebase-firestore.js";

// =========================================
// 1. CONFIGURAÇÃO FIREBASE (Vercel)
// =========================================
const firebaseConfig = {
  apiKey: "AIzaSyDWhGtdl9A9CeWFLX1ZKN3ORju0K_6Up9g",
  authDomain: "myli-30303.firebaseapp.com",
  projectId: "myli-30303",
  storageBucket: "myli-30303.firebasestorage.app",
  messagingSenderId: "861514681124",
  appId: "1:861514681124:web:b33c9a52a08260c48f41b1",
  measurementId: "G-XSFF822HWW"
};

// Verifica se as chaves foram preenchidas ou não.
const isFirebaseConfigured = firebaseConfig.apiKey !== "COLOQUE_AQUI_SUA_API_KEY";
let db = null;
const DOC_ID = "meu_rpg_pessoal";

if (isFirebaseConfigured) {
    const app = initializeApp(firebaseConfig);
    db = getFirestore(app);
}

// =========================================
// 2. ESTADO DA APLICAÇÃO (GAMIFICAÇÃO)
// =========================================
let appState = {
    xpTotal: 0,
    dailyXp: 0,
    lastDate: new Date().toDateString(),
    attributes: [], // Gamificação Dinâmica [{id, name, xp, level}]
    tasks: [],
    habits: [],
    activities: [],
    history: []
};

const difficultyMap = {
    'easy': { label: 'Fácil', xp: 30, colorClass: 'badge-easy' },
    'medium': { label: 'Médio', xp: 60, colorClass: 'badge-medium' },
    'hard': { label: 'Difícil', xp: 100, colorClass: 'badge-hard' }
};

// Utilitário de XSS seguro
function escapeHTML(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Tratamento de Erros de UI
function showError(inputId, message) {
    const errorEl = document.getElementById(`error-${inputId}`);
    if (errorEl) { errorEl.textContent = message; errorEl.classList.add('active'); }
}
function hideErrors() {
    document.querySelectorAll('.error-msg').forEach(el => { el.textContent = ''; el.classList.remove('active'); });
}
function checkDuplicate(name, list) {
    return list.some(item => item.name.toLowerCase() === name.toLowerCase());
}

// =========================================
// 3. PERSISTÊNCIA (FIREBASE + LOCALSTORAGE FALLBACK)
// =========================================
async function loadState() {
    showSyncStatus("Carregando...", "active");
    if (isFirebaseConfigured) {
        try {
            const docRef = doc(db, "rpg_dashboard", DOC_ID);
            const docSnap = await getDoc(docRef);
            if (docSnap.exists()) {
                const data = docSnap.data();
                appState = { ...appState, ...data };
                // Assegurar arrays caso o DB venha mal formatado
                if(!appState.attributes) appState.attributes = [];
                if(!appState.tasks) appState.tasks = [];
                if(!appState.habits) appState.habits = [];
                if(!appState.activities) appState.activities = [];
                if(!appState.history) appState.history = [];
            }
            showSyncStatus("Banco sincronizado", "success");
        } catch (e) {
            console.error("Erro no Firebase:", e);
            fallbackLoad();
            showSyncStatus("Modo Local (Erro BD)", "error");
        }
    } else {
        fallbackLoad();
        showSyncStatus("Modo Offline (Configure as Chaves)", "error");
    }
    
    setTimeout(() => document.getElementById('sync-status').classList.remove('active', 'success', 'error'), 3000);
    
    resetIfNewDay();
    checkOverdueActivities();
    saveAndRenderAll(false); // Renderiza sem forçar salvamento extra na carga
}

function fallbackLoad() {
    const saved = localStorage.getItem('notionRpgState');
    if (saved) {
        const parsed = JSON.parse(saved);
        appState = { ...appState, ...parsed };
    }
}

async function saveState() {
    if (isFirebaseConfigured) {
        try {
            await setDoc(doc(db, "rpg_dashboard", DOC_ID), appState);
        } catch (e) {
            console.error("Erro ao salvar no Firebase:", e);
            localStorage.setItem('notionRpgState', JSON.stringify(appState));
        }
    } else {
        localStorage.setItem('notionRpgState', JSON.stringify(appState));
    }
}

function showSyncStatus(msg, statusClass) {
    const el = document.getElementById('sync-status');
    el.className = `sync-status ${statusClass}`;
    el.innerHTML = msg;
}

// =========================================
// 4. LÓGICA DE GAMIFICAÇÃO & TEMPO
// =========================================
function resetIfNewDay() {
    const today = new Date().toDateString();
    if (appState.lastDate !== today) {
        appState.dailyXp = 0;
        appState.tasks.forEach(task => task.completed = false);
        appState.habits.forEach(habit => {
            if (!habit.completedToday) habit.streak = 0; 
            habit.completedToday = false; 
        });
        appState.lastDate = today;
        addHistoryItem("Novo dia iniciado! Progresso diário resetado.", "ri-sun-line", "text-yellow");
        saveState();
    }
}

function checkOverdueActivities() {
    const now = Date.now();
    let changed = false;
    appState.activities.forEach(act => {
        if (!act.completed && !act.failed && now > act.dueDate) {
            act.failed = true;
            appState.xpTotal = Math.max(0, appState.xpTotal - act.xp);
            addHistoryItem(`Falhou no prazo: ${act.name} (-${act.xp} XP)`, "ri-close-circle-line", "text-red");
            changed = true;
        }
    });
    if (changed) saveAndRenderAll();
}

// Aplica XP num atributo customizado
function applyAttributeXp(attrId, xpGained, isSubtract = false) {
    if(!attrId || attrId === "none") return;
    const attr = appState.attributes.find(a => a.id === attrId);
    if(attr) {
        if (isSubtract) {
            attr.xp = Math.max(0, attr.xp - xpGained);
        } else {
            attr.xp += xpGained;
        }
        attr.level = Math.floor(attr.xp / 100) + 1; // 1 level a cada 100 XP
    }
}

function getAttributeName(attrId) {
    if(!attrId || attrId === "none") return "";
    const attr = appState.attributes.find(a => a.id === attrId);
    return attr ? attr.name : "";
}

// =========================================
// 5. RENDERIZAÇÃO DE UI
// =========================================
function populateAttributeSelects() {
    const selects = ['input-task-attr', 'input-habit-attr', 'input-act-attr'];
    const optionsHTML = `<option value="none" selected>Geral (Nenhum Atributo Específico)</option>` + 
        appState.attributes.map(a => `<option value="${a.id}">${escapeHTML(a.name)} (Lvl ${a.level})</option>`).join('');
    
    selects.forEach(id => {
        const el = document.getElementById(id);
        if(el) el.innerHTML = optionsHTML;
    });
}

function renderAttributes() {
    const container = document.getElementById('attributes-container');
    container.innerHTML = '';
    
    if (appState.attributes.length === 0) {
        container.innerHTML = '<p class="text-sub text-sm">Nenhum atributo criado. Adicione um acima.</p>';
        return;
    }

    appState.attributes.forEach(attr => {
        const progress = attr.xp % 100;
        const div = document.createElement('div');
        div.className = 'attr-item';
        div.innerHTML = `
            <div class="flex-between text-sm mb-1">
                <span class="font-medium">${escapeHTML(attr.name)}</span>
                <span class="text-sub">Lvl ${attr.level}</span>
            </div>
            <div class="progress-track small">
                <div class="progress-fill blue-fill" style="width: ${progress}%"></div>
            </div>
            <button class="icon-btn delete attr-actions" onclick="window.removeAttr('${attr.id}')" title="Apagar"><i class="ri-close-line"></i></button>
        `;
        container.appendChild(div);
    });
}

function renderActivities() {
    const container = document.getElementById('activities-container');
    container.innerHTML = '';
    if (appState.activities.length === 0) {
        container.innerHTML = '<p class="text-sub text-sm" style="padding: 8px 4px;">Nenhuma atividade agendada.</p>'; return;
    }

    const sorted = [...appState.activities].sort((a, b) => a.dueDate - b.dueDate);
    sorted.forEach(act => {
        const diffInfo = difficultyMap[act.difficulty];
        const safeName = escapeHTML(act.name);
        const attrName = getAttributeName(act.attrId);
        const attrBadge = attrName ? `<span class="item-attr-badge">${escapeHTML(attrName)}</span>` : '';
        const dateStr = new Date(act.dueDate).toLocaleDateString('pt-BR', { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit' });
        
        const div = document.createElement('div');
        div.className = `notion-item ${act.completed ? 'completed' : ''} ${act.failed ? 'failed' : ''}`;
        div.innerHTML = `
            <label class="checkbox-wrapper">
                <input type="checkbox" ${act.completed ? 'checked' : ''} ${(act.failed || act.completed) ? 'disabled' : ''} onchange="window.toggleActivity('${act.id}')">
                <div class="custom-check"><i class="ri-check-line"></i></div>
            </label>
            <div class="item-info">
                <span class="item-name">${safeName}</span>
                <span class="badge ${diffInfo.colorClass}">${diffInfo.label}</span>
                ${attrBadge}
                <span class="item-meta ${act.failed ? 'text-red' : ''}"><i class="ri-time-line"></i> ${dateStr}</span>
                <span class="item-meta">${act.failed ? '-' : '+'}${act.xp} XP</span>
            </div>
            <div class="item-actions">
                <button class="icon-btn delete" onclick="window.removeActivity('${act.id}')" title="Excluir"><i class="ri-delete-bin-line"></i></button>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderTasks() {
    const container = document.getElementById('tasks-container');
    container.innerHTML = '';
    if (appState.tasks.length === 0) {
        container.innerHTML = '<p class="text-sub text-sm" style="padding: 8px 4px;">Planeje seu dia criando uma nova missão.</p>'; return;
    }
    appState.tasks.forEach(task => {
        const attrName = getAttributeName(task.attrId);
        const attrBadge = attrName ? `<span class="item-attr-badge">${escapeHTML(attrName)}</span>` : '';
        const div = document.createElement('div');
        div.className = `notion-item ${task.completed ? 'completed' : ''}`;
        div.innerHTML = `
            <label class="checkbox-wrapper">
                <input type="checkbox" ${task.completed ? 'checked' : ''} onchange="window.toggleTask('${task.id}')">
                <div class="custom-check"><i class="ri-check-line"></i></div>
            </label>
            <div class="item-info">
                <span class="item-name">${escapeHTML(task.name)}</span>
                ${attrBadge}
                <span class="item-meta">+${task.xp} XP</span>
            </div>
            <div class="item-actions">
                <button class="icon-btn delete" onclick="window.removeTask('${task.id}')" title="Excluir"><i class="ri-delete-bin-line"></i></button>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderHabits() {
    const container = document.getElementById('habits-container');
    container.innerHTML = '';
    if (appState.habits.length === 0) {
        container.innerHTML = '<p class="text-sub text-sm" style="padding: 8px 4px;">Nenhum hábito rastreado.</p>'; return;
    }
    appState.habits.forEach(habit => {
        const attrName = getAttributeName(habit.attrId);
        const attrBadge = attrName ? `<span class="item-attr-badge">${escapeHTML(attrName)}</span>` : '';
        const div = document.createElement('div');
        div.className = `notion-item ${habit.completedToday ? 'completed' : ''}`;
        div.innerHTML = `
            <label class="checkbox-wrapper">
                <input type="checkbox" ${habit.completedToday ? 'checked' : ''} onchange="window.toggleHabit('${habit.id}')">
                <div class="custom-check"><i class="ri-check-line"></i></div>
            </label>
            <div class="item-info">
                <span class="item-name">${escapeHTML(habit.name)}</span>
                ${attrBadge}
                <span class="item-meta" style="color: var(--orange); background: transparent; padding: 0;">🔥 ${habit.streak} dia(s)</span>
            </div>
            <div class="item-actions">
                <button class="icon-btn delete" onclick="window.removeHabit('${habit.id}')" title="Excluir"><i class="ri-delete-bin-line"></i></button>
            </div>
        `;
        container.appendChild(div);
    });
}

function renderHistory() {
    const container = document.getElementById('history-container');
    container.innerHTML = '';
    const recent = appState.history.slice(-6).reverse();
    if (recent.length === 0) { container.innerHTML = '<p class="text-sub text-sm">Sem histórico recente.</p>'; return; }

    recent.forEach(item => {
        const li = document.createElement('li');
        li.innerHTML = `<i class="${item.icon} ${item.colorClass}"></i>
            <div><span>${escapeHTML(item.text)}</span><span class="time">${item.time}</span></div>`;
        container.appendChild(li);
    });
}

function updateUI() {
    // 1000 XP por level Global
    const globalLevel = Math.floor(appState.xpTotal / 1000) + 1;
    const currentGlobalXp = appState.xpTotal % 1000;
    const xpPercent = Math.min((currentGlobalXp / 1000) * 100, 100);
    
    document.getElementById('global-level-text').innerText = `Lvl ${globalLevel} • XP: ${appState.xpTotal}`;
    document.getElementById('xp-display').innerText = `${currentGlobalXp} / 1000`;
    document.getElementById('xp-bar').style.width = `${xpPercent}%`;
    document.getElementById('today-xp').innerText = `+${appState.dailyXp}`;

    const totalTasks = appState.tasks.length;
    const completedTasks = appState.tasks.filter(t => t.completed).length;
    document.getElementById('today-goals').innerText = `${completedTasks}/${totalTasks}`;
    
    const dayProgress = totalTasks === 0 ? 0 : (completedTasks / totalTasks) * 100;
    document.getElementById('day-progress-bar').style.width = `${dayProgress}%`;
    
    const taskProgressText = document.getElementById('task-progress-text');
    if (totalTasks > 0 && completedTasks === totalTasks) {
        taskProgressText.innerText = "Dia Completo! 🎉"; taskProgressText.className = "text-sm text-green font-medium";
    } else {
        taskProgressText.innerText = `${Math.round(dayProgress)}% Concluído`; taskProgressText.className = "text-sub text-sm";
    }
}

function saveAndRenderAll(doSave = true) {
    if(doSave) saveState();
    populateAttributeSelects();
    renderAttributes();
    renderActivities();
    renderTasks();
    renderHabits();
    renderHistory();
    updateUI();
}

// =========================================
// 6. AÇÕES DO USUÁRIO
// =========================================
function addHistoryItem(text, icon = "ri-information-line", colorClass = "text-main") {
    const now = new Date();
    const time = now.getHours().toString().padStart(2, '0') + ':' + now.getMinutes().toString().padStart(2, '0');
    appState.history.push({ id: Date.now().toString(), text, time, icon, colorClass });
    if (appState.history.length > 30) appState.history.shift();
}

// Atributos
window.addAttr = function(name) {
    if(checkDuplicate(name, appState.attributes)) return false;
    appState.attributes.push({ id: 'att_' + Date.now(), name, xp: 0, level: 1 });
    addHistoryItem(`Atributo criado: ${name}`, "ri-medal-line", "text-blue");
    saveAndRenderAll();
    return true;
};
window.removeAttr = function(id) {
    appState.attributes = appState.attributes.filter(a => a.id !== id);
    addHistoryItem("Atributo excluído", "ri-delete-bin-line", "text-sub");
    saveAndRenderAll();
};

// Atividades
window.toggleActivity = function(id) {
    const act = appState.activities.find(a => a.id === id);
    if (!act || act.failed) return;
    act.completed = !act.completed;
    if (act.completed) {
        appState.xpTotal += act.xp; appState.dailyXp += act.xp;
        applyAttributeXp(act.attrId, act.xp, false);
        addHistoryItem(`Atividade Entregue: ${act.name}`, "ri-check-double-line", "text-green");
    } else {
        appState.xpTotal = Math.max(0, appState.xpTotal - act.xp); appState.dailyXp = Math.max(0, appState.dailyXp - act.xp);
        applyAttributeXp(act.attrId, act.xp, true);
        addHistoryItem(`Desmarcou: ${act.name}`, "ri-arrow-go-back-line", "text-sub");
    }
    saveAndRenderAll();
};
window.removeActivity = function(id) {
    const act = appState.activities.find(a => a.id === id);
    if (act && act.completed) { 
        appState.xpTotal = Math.max(0, appState.xpTotal - act.xp); 
        appState.dailyXp = Math.max(0, appState.dailyXp - act.xp); 
        applyAttributeXp(act.attrId, act.xp, true);
    }
    appState.activities = appState.activities.filter(a => a.id !== id);
    addHistoryItem(`Removeu atividade: ${act ? act.name : ''}`, "ri-delete-bin-line", "text-sub");
    saveAndRenderAll();
};

// Tarefas
window.toggleTask = function(id) {
    const task = appState.tasks.find(t => t.id === id);
    if (!task) return;
    task.completed = !task.completed;
    if (task.completed) {
        appState.xpTotal += task.xp; appState.dailyXp += task.xp;
        applyAttributeXp(task.attrId, task.xp, false);
        addHistoryItem(`Concluiu: ${task.name}`, "ri-check-line", "text-green");
    } else {
        appState.xpTotal = Math.max(0, appState.xpTotal - task.xp); appState.dailyXp = Math.max(0, appState.dailyXp - task.xp);
        applyAttributeXp(task.attrId, task.xp, true);
        addHistoryItem(`Desmarcou: ${task.name}`, "ri-arrow-go-back-line", "text-sub");
    }
    saveAndRenderAll();
};
window.removeTask = function(id) {
    const task = appState.tasks.find(t => t.id === id);
    if (task && task.completed) {
        appState.xpTotal = Math.max(0, appState.xpTotal - task.xp);
        appState.dailyXp = Math.max(0, appState.dailyXp - task.xp);
        applyAttributeXp(task.attrId, task.xp, true);
    }
    appState.tasks = appState.tasks.filter(t => t.id !== id);
    addHistoryItem(`Deletou tarefa: ${task ? task.name : ''}`, "ri-delete-bin-line", "text-sub");
    saveAndRenderAll();
};

// Hábitos
window.toggleHabit = function(id) {
    const habit = appState.habits.find(h => h.id === id);
    if (!habit) return;
    habit.completedToday = !habit.completedToday;
    if (habit.completedToday) {
        habit.streak += 1; appState.xpTotal += 10; appState.dailyXp += 10;
        applyAttributeXp(habit.attrId, 10, false);
        addHistoryItem(`Hábito feito: ${habit.name}`, "ri-fire-fill", "text-orange");
    } else {
        habit.streak = Math.max(0, habit.streak - 1); appState.xpTotal = Math.max(0, appState.xpTotal - 10); appState.dailyXp = Math.max(0, appState.dailyXp - 10);
        applyAttributeXp(habit.attrId, 10, true);
        addHistoryItem(`Hábito desmarcado: ${habit.name}`, "ri-arrow-go-back-line", "text-sub");
    }
    saveAndRenderAll();
};
window.removeHabit = function(id) {
    const habit = appState.habits.find(h => h.id === id);
    if (habit && habit.completedToday) {
        appState.xpTotal = Math.max(0, appState.xpTotal - 10);
        appState.dailyXp = Math.max(0, appState.dailyXp - 10);
        applyAttributeXp(habit.attrId, 10, true);
    }
    appState.habits = appState.habits.filter(h => h.id !== id);
    addHistoryItem(`Hábito excluído: ${habit ? habit.name : ''}`, "ri-delete-bin-line", "text-sub");
    saveAndRenderAll();
};

// =========================================
// 7. EVENTOS E INICIALIZAÇÃO
// =========================================
function setupModals() {
    const modals = [
        { id: 'modal-attr', btnOpen: 'btn-open-attr', btnClose: '.close-attr', saveBtn: 'save-attr' },
        { id: 'modal-task', btnOpen: 'btn-open-task', btnClose: '.close-task', saveBtn: 'save-task' },
        { id: 'modal-habit', btnOpen: 'btn-open-habit', btnClose: '.close-habit', saveBtn: 'save-habit' },
        { id: 'modal-activity', btnOpen: 'btn-open-activity', btnClose: '.close-activity', saveBtn: 'save-activity' },
        { id: 'modal-log', btnOpen: 'btn-open-log', btnClose: '.close-log', saveBtn: 'save-log' }
    ];

    modals.forEach(m => {
        const overlay = document.getElementById(m.id);
        const btnOpen = document.getElementById(m.btnOpen);
        const btnCloseList = overlay.querySelectorAll(m.btnClose);
        const saveBtn = document.getElementById(m.saveBtn);
        
        if(btnOpen) btnOpen.addEventListener('click', () => {
            hideErrors(); overlay.classList.add('active');
            setTimeout(() => { const i = overlay.querySelector('input'); if(i) i.focus(); }, 100);
        });

        const closeModalFn = () => { overlay.classList.remove('active'); overlay.querySelectorAll('input, select').forEach(i => i.value = ''); hideErrors(); };
        btnCloseList.forEach(btn => btn.addEventListener('click', closeModalFn));
        overlay.addEventListener('click', (e) => { if (e.target === overlay) closeModalFn(); });
        overlay.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModalFn(); if (e.key === 'Enter') saveBtn.click(); });
    });

    document.getElementById('save-attr').addEventListener('click', () => {
        hideErrors(); const name = document.getElementById('input-attr-name').value.trim();
        if(!name) { showError('attr-name', 'Nome obrigatório.'); return; }
        if(window.addAttr(name)) document.querySelector('.close-attr').click();
    });

    document.getElementById('save-task').addEventListener('click', () => {
        hideErrors();
        const name = document.getElementById('input-task-name').value.trim();
        const xp = document.getElementById('input-task-xp').value;
        const attrId = document.getElementById('input-task-attr').value;
        
        let valid = true;
        if (!name) { showError('task-name', 'Obrigatório.'); valid = false; }
        else if (checkDuplicate(name, appState.tasks)) { showError('task-name', 'Já existe.'); valid = false; }
        if (!xp || xp < 1 || isNaN(xp)) { showError('task-xp', 'XP inteiro positivo.'); valid = false; }

        if (valid) {
            appState.tasks.push({ id: 't_' + Date.now(), name, xp: parseInt(xp), attrId, completed: false });
            addHistoryItem(`Criou tarefa: ${name}`, "ri-add-line", "text-blue");
            saveAndRenderAll();
            document.querySelector('.close-task').click();
        }
    });

    document.getElementById('save-habit').addEventListener('click', () => {
        hideErrors();
        const name = document.getElementById('input-habit-name').value.trim();
        const attrId = document.getElementById('input-habit-attr').value;
        
        if (!name) { showError('habit-name', 'Obrigatório.'); return; }
        if (checkDuplicate(name, appState.habits)) { showError('habit-name', 'Já existe.'); return; }
        
        appState.habits.push({ id: 'h_' + Date.now(), name, attrId, streak: 0, completedToday: false });
        addHistoryItem(`Novo hábito: ${name}`, "ri-loop-left-line", "text-blue");
        saveAndRenderAll();
        document.querySelector('.close-habit').click();
    });

    document.getElementById('save-activity').addEventListener('click', () => {
        hideErrors();
        const name = document.getElementById('input-act-name').value.trim();
        const dateStr = document.getElementById('input-act-date').value;
        const diff = document.getElementById('input-act-diff').value;
        const attrId = document.getElementById('input-act-attr').value;
        let valid = true;
        
        if (!name) { showError('act-name', 'Nome obrigatório.'); valid = false; }
        if (!dateStr) { showError('act-date', 'Data/hora obrigatória.'); valid = false; }
        else if (new Date(dateStr).getTime() <= Date.now()) { showError('act-date', 'A data deve ser no futuro.'); valid = false; }
        if (!diff) { showError('act-diff', 'Selecione uma dificuldade.'); valid = false; }

        if (valid) {
            const xpVal = difficultyMap[diff].xp;
            appState.activities.push({ id: 'a_' + Date.now(), name, dueDate: new Date(dateStr).getTime(), difficulty: diff, attrId, xp: xpVal, completed: false, failed: false });
            addHistoryItem(`Agendou: ${name}`, "ri-timer-line", "text-blue");
            saveAndRenderAll();
            document.querySelector('.close-activity').click();
        }
    });

    document.getElementById('save-log').addEventListener('click', () => {
        hideErrors(); const desc = document.getElementById('input-log-desc').value.trim();
        if (!desc) { showError('log-desc', 'A descrição é obrigatória.'); return; }
        addHistoryItem(desc, "ri-edit-line", "text-main"); saveAndRenderAll();
        document.querySelector('.close-log').click();
    });
}

// Start
document.addEventListener('DOMContentLoaded', () => {
    setupModals();
    loadState(); // Carega LocalStorage ou Firebase
    setInterval(checkOverdueActivities, 60000); // Valida atrasos a cada minuto
});

