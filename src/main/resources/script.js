document.getElementById('fileInput').addEventListener('change', uploadFile);

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