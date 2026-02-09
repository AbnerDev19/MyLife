// --- ESTADO INICIAL DO USUÁRIO ---
const user = {
    name: "Player 1",
    level: 5,
    xp: 450,
    xpToNextLevel: 1000,
    streak: 12, // Dias seguidos
    coins: 150,
    attributes: {
        intelligence: { name: "Inteligência", value: 45, icon: "psychology" },
        strength: { name: "Disciplina", value: 60, icon: "fitness_center" },
        health: { name: "Saúde", value: 30, icon: "favorite" },
        social: { name: "Social", value: 20, icon: "groups" }
    }
};

// --- DADOS DOS HÁBITOS (MOCK) ---
const habits = [
    { id: 1, title: "Ler 10 páginas", xp: 50, attr: "intelligence", completed: false },
    { id: 2, title: "Treino de Musculação", xp: 100, attr: "strength", completed: false },
    { id: 3, title: "Beber 3L de água", xp: 30, attr: "health", completed: false },
    { id: 4, title: "Meditação", xp: 40, attr: "intelligence", completed: true }, // Já completado exemplo
    { id: 5, title: "Networking no LinkedIn", xp: 60, attr: "social", completed: false }
];

// --- FUNÇÕES DE RENDERIZAÇÃO ---

// Atualiza a barra de XP e Nível no Header
function updateHeader() {
    document.getElementById('user-level').innerText = user.level;
    document.getElementById('xp-display').innerText = `${user.xp} / ${user.xpToNextLevel}`;
    
    const percentage = (user.xp / user.xpToNextLevel) * 100;
    document.getElementById('xp-bar').style.width = `${percentage}%`;
}

// Renderiza a página Dashboard
function renderDashboard() {
    const main = document.getElementById('main-content');
    const completedHabits = habits.filter(h => h.completed).length;
    const totalHabits = habits.length;
    
    main.innerHTML = `
        <div class="section-title">Visão Geral</div>
        <div class="grid-stats">
            <div class="stat-card">
                <span class="stat-value">${user.streak} <span style="font-size:16px">🔥</span></span>
                <span class="stat-label">Dias em Sequência</span>
            </div>
            <div class="stat-card">
                <span class="stat-value">${Math.round((completedHabits/totalHabits)*100)}%</span>
                <span class="stat-label">Eficiência Hoje</span>
            </div>
        </div>

        <div class="section-title">Hábitos de Hoje</div>
        <div class="habit-list" id="dashboard-habits">
            </div>
    `;
    
    renderHabitList('dashboard-habits');
}

// Renderiza a lista de hábitos
function renderHabitList(containerId) {
    const container = document.getElementById(containerId);
    container.innerHTML = '';

    habits.forEach(habit => {
        const item = document.createElement('div');
        item.className = `habit-card ${habit.completed ? 'completed' : ''}`;
        item.onclick = () => toggleHabit(habit.id); // Clique no card inteiro

        item.innerHTML = `
            <div class="habit-info">
                <h4>${habit.title}</h4>
                <span class="habit-tag">+${habit.xp} XP | ${user.attributes[habit.attr].name}</span>
            </div>
            <button class="check-btn">
                <span class="material-icons-round">check</span>
            </button>
        `;
        container.appendChild(item);
    });
}

// Renderiza a página de Atributos (RPG)
function renderRPG() {
    const main = document.getElementById('main-content');
    let htmlContent = `<div class="section-title">Seus Atributos</div><div class="attr-grid">`;

    for (let key in user.attributes) {
        const attr = user.attributes[key];
        htmlContent += `
            <div class="attr-card">
                <div class="attr-icon">
                    <span class="material-icons-round">${attr.icon}</span>
                </div>
                <div class="attr-details">
                    <div style="display:flex; justify-content:space-between; margin-bottom:5px;">
                        <strong>${attr.name}</strong>
                        <span>Lvl ${Math.floor(attr.value / 10)}</span>
                    </div>
                    <div class="attr-bar">
                        <div class="attr-fill" style="width: ${attr.value}%"></div>
                    </div>
                </div>
            </div>
        `;
    }

    htmlContent += `</div>`;
    main.innerHTML = htmlContent;
}

// --- LÓGICA DE AÇÃO ---

// Marcar/Desmarcar Hábito
function toggleHabit(id) {
    const habit = habits.find(h => h.id === id);
    if (!habit) return;

    habit.completed = !habit.completed;

    // Atualizar XP e Atributos
    if (habit.completed) {
        user.xp += habit.xp;
        user.attributes[habit.attr].value += 1; // Sobe o atributo específico
        checkLevelUp();
    } else {
        user.xp -= habit.xp;
        user.attributes[habit.attr].value -= 1;
    }

    updateHeader();
    
    // Re-renderizar a página atual para mostrar a animação
    const currentPage = document.querySelector('.nav-item.active span:last-child').innerText;
    if(currentPage === 'Início' || currentPage === 'Hábitos') {
        renderDashboard(); // Simplificado para o exemplo
    }
}

function checkLevelUp() {
    if (user.xp >= user.xpToNextLevel) {
        user.level++;
        user.xp = user.xp - user.xpToNextLevel;
        user.xpToNextLevel = Math.floor(user.xpToNextLevel * 1.2); // Dificuldade aumenta
        alert(`🎉 LEVEL UP! Você alcançou o nível ${user.level}!`);
    }
}

// --- NAVEGAÇÃO ---

function navigate(page) {
    // Atualiza ícones ativos
    document.querySelectorAll('.nav-item').forEach(btn => btn.classList.remove('active'));
    event.currentTarget.classList.add('active');

    // Roteamento simples
    if (page === 'dashboard' || page === 'habits') {
        renderDashboard();
    } else if (page === 'rpg') {
        renderRPG();
    } else {
        document.getElementById('main-content').innerHTML = `
            <div style="padding:20px; text-align:center; color:#666; margin-top:50px;">
                <span class="material-icons-round" style="font-size:48px;">lock</span>
                <p>Área de Missões/Conquistas em desenvolvimento...</p>
            </div>
        `;
    }
}

// Inicialização
document.addEventListener('DOMContentLoaded', () => {
    updateHeader();
    renderDashboard();
});