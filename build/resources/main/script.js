document.getElementById('fileInput').addEventListener('change', uploadFile);

// Drag & Drop
const uploadArea = document.getElementById('uploadArea');

['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
    uploadArea.addEventListener(eventName, preventDefaults, false);
});

function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
}

['dragenter', 'dragover'].forEach(eventName => {
    uploadArea.addEventListener(eventName, highlight, false);
});

['dragleave', 'drop'].forEach(eventName => {
    uploadArea.addEventListener(eventName, unhighlight, false);
});

function highlight() {
    uploadArea.classList.add('drag-over');
}

function unhighlight() {
    uploadArea.classList.remove('drag-over');
}

uploadArea.addEventListener('drop', handleDrop, false);

function handleDrop(e) {
    const dt = e.dataTransfer;
    const files = dt.files;

    if (files.length) {
        document.getElementById('fileInput').files = files;
        uploadFile({ target: document.getElementById('fileInput') });
    }
}

// Загрузка статистики
async function loadStats() {
    try {
        const response = await fetch('/stats');
        if (response.ok) {
            const stats = await response.json();
            document.getElementById('totalFiles').textContent = stats.totalFiles;
            document.getElementById('totalSize').textContent = stats.totalSize;
        }
    } catch (error) {
        console.error('Ошибка загрузки статистики:', error);
    }
}

async function uploadFile(event) {
    const file = event.target.files[0];
    if (!file) return;

    showProgress(0);

    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await fetch('/upload', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            const link = await response.text();
            showResult(link);
            loadStats();
        } else {
            alert('Ошибка загрузки');
        }
    } catch (error) {
        alert('Error: ' + error.message);
    } finally {
        hideProgress();
    }
}

function showProgress(percent) {
    document.getElementById('progress').classList.remove('hidden');
    document.getElementById('progressText').textContent = percent + '%';
}

function hideProgress() {
    document.getElementById('progress').classList.add('hidden');
}

function showResult(link) {
    document.getElementById('downloadLink').textContent = link;
    document.getElementById('downloadLink').href = link;
    document.getElementById('result').classList.remove('hidden');
}

// Загружаем статистику при загрузке страницы
document.addEventListener('DOMContentLoaded', loadStats);