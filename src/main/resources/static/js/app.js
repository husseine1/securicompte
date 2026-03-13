/* ============================================
   SECURICOMPTE - JavaScript principal
   ============================================ */

document.addEventListener('DOMContentLoaded', function() {

    // Upload zone drag & drop
    const uploadZone = document.getElementById('uploadZone');
    const fichierInput = document.getElementById('fichierInput');

    if (uploadZone && fichierInput) {
        uploadZone.addEventListener('click', () => fichierInput.click());
        uploadZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadZone.classList.add('drag-over');
        });
        uploadZone.addEventListener('dragleave', () => uploadZone.classList.remove('drag-over'));
        uploadZone.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadZone.classList.remove('drag-over');
            const dt = new DataTransfer();
            dt.items.add(e.dataTransfer.files[0]);
            fichierInput.files = dt.files;
            updateFileName(fichierInput);
        });
    }

    // Formulaire upload - loader
    const uploadForm = document.getElementById('uploadForm');
    if (uploadForm) {
        uploadForm.addEventListener('submit', function() {
            const submitBtn = document.getElementById('submitBtn');
            if (submitBtn) {
                submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Import en cours...';
                submitBtn.disabled = true;
            }
        });
    }

    // Dashboard charts
    initCharts();
});

function updateFileName(input) {
    const display = document.getElementById('fileNameDisplay');
    if (input.files.length > 0 && display) {
        display.textContent = '✓ ' + input.files[0].name;
        display.classList.remove('d-none');
    }
}

function regulariser(impayeId) {
    const commentaire = prompt('Commentaire de régularisation (optionnel) :') || '';
    if (!confirm('Confirmer la régularisation de cet impayé ?')) return;

    fetch(`/impayes/${impayeId}/regulariser`, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `commentaire=${encodeURIComponent(commentaire)}`
    })
    .then(r => r.text())
    .then(result => {
        if (result === 'OK') {
            location.reload();
        } else {
            alert('Erreur lors de la régularisation');
        }
    })
    .catch(() => alert('Erreur de connexion'));
}

function initCharts() {
    // Graphique évolution impayés par mois
    const ctxMois = document.getElementById('chartImpayes');
    if (ctxMois && typeof statsMois !== 'undefined' && statsMois.length > 0) {
        const labels = statsMois.map(s => (s.moisNom || s.mois) + ' ' + s.annee).reverse();
        const data = statsMois.map(s => s.nbImpayes).reverse();
        new Chart(ctxMois, {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: 'Impayés',
                    data,
                    borderColor: '#dc2626',
                    backgroundColor: 'rgba(220,38,38,0.1)',
                    fill: true,
                    tension: 0.4,
                    pointRadius: 4,
                    pointBackgroundColor: '#dc2626'
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, grid: { color: '#f0f0f0' } },
                    x: { grid: { display: false } }
                }
            }
        });
    }

    // Graphique par agence
    const ctxAgences = document.getElementById('chartAgences');
    if (ctxAgences && typeof statsAgences !== 'undefined' && statsAgences.length > 0) {
        const colors = ['#dc2626','#ea580c','#ca8a04','#16a34a','#2563eb','#7c3aed','#db2777','#0891b2'];
        new Chart(ctxAgences, {
            type: 'doughnut',
            data: {
                labels: statsAgences.map(s => s.agence),
                datasets: [{
                    data: statsAgences.map(s => s.nbImpayes),
                    backgroundColor: colors,
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: { position: 'bottom', labels: { font: { size: 11 } } }
                }
            }
        });
    }
}
