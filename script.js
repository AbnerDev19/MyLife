// Estado do Usuário
let userState = {
    xp: 450,
    maxXp: 1000,
    dailyXP: 0,
    tasksTotal: 2,
    tasksDone: 0
};

// Elementos
const xpDisplay = document.getElementById('xp-display');
const xpBar = document.getElementById('xp-bar');
const dayProgressBar = document.getElementById('day-progress-bar');
const taskProgressText = document.getElementById('task-progress-text');
const todayXpDisplay = document.getElementById('today-xp');
const todayGoalsDisplay = document.getElementById('today-goals');

// Citações Aleatórias
const quotes = [
    "A constância vence o talento. 🚀",
    "Um passo de cada vez, sempre em frente. ⚔️",
    "Hoje é um ótimo dia para evoluir. 💎",
    "Foco na missão, recompensa na mão. 🎯"
];
document.getElementById('daily-quote').innerText = quotes[Math.floor(Math.random() * quotes.length)];

function toggleTask(checkbox, xpValue) {
    const card = checkbox.closest('.task-card');

    if (checkbox.checked) {
        // Completar
        card.classList.add('completed');
        userState.xp += xpValue;
        userState.dailyXP += xpValue;
        userState.tasksDone++;
    } else {
        // Desfazer
        card.classList.remove('completed');
        userState.xp -= xpValue;
        userState.dailyXP -= xpValue;
        userState.tasksDone--;
    }

    updateUI();
}

function updateUI() {
    // 1. Barra de XP Geral
    let xpPercent = (userState.xp / userState.maxXp) * 100;
    xpBar.style.width = `${Math.min(xpPercent, 100)}%`;
    xpDisplay.innerText = `${userState.xp} / ${userState.maxXp}`;

    // 2. Barra de Progresso do Dia
    let dayPercent = (userState.tasksDone / userState.tasksTotal) * 100;
    dayProgressBar.style.width = `${dayPercent}%`;
    taskProgressText.innerText = `${Math.round(dayPercent)}% Concluído`;

    // 3. Mini Stats Sidebar
    todayXpDisplay.innerText = `+${userState.dailyXP}`;
    todayGoalsDisplay.innerText = `${userState.tasksDone}/${userState.tasksTotal}`;

    // Cor condicional para Stats
    if (userState.tasksDone === userState.tasksTotal) {
        taskProgressText.innerText = "Dia Completo! 🎉";
        taskProgressText.style.color = "var(--neon-green)";
    }
}