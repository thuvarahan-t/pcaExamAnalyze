(() => {
    'use strict';
    const menuToggle = document.getElementById('adminMenuToggle');
    const sidebar = document.getElementById('adminSidebar');
    menuToggle?.addEventListener('click', () => {
        const open = sidebar.classList.toggle('menu-open');
        menuToggle.setAttribute('aria-expanded', String(open));
        menuToggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
        menuToggle.innerHTML = open ? '<i class="bi bi-x-lg"></i>' : '<i class="bi bi-list"></i>';
    });
    const purchaseFilter = document.getElementById('purchaseFilter');
    purchaseFilter?.addEventListener('input', () => {
        const query = purchaseFilter.value.trim().toLocaleLowerCase(); let matches = 0;
        document.querySelectorAll('[data-purchase-row]').forEach(row => {
            const text = row.cells[0].textContent + ' ' + row.cells[1].textContent;
            row.hidden = !text.toLocaleLowerCase().includes(query); if (!row.hidden) matches++;
        });
        document.getElementById('purchaseFilterStatus').textContent = matches ? matches + ' students' : 'No matching students';
    });
    const upload = document.getElementById('posterUpload');
    if (!upload) return;
    let objectUrl;
    upload.addEventListener('change', () => {
        const file = upload.files[0]; const error = document.getElementById('posterError'); error.textContent = ''; upload.setCustomValidity('');
        if (!file) return;
        if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
            error.textContent = 'Choose a JPEG or PNG poster, 5 MB or smaller.'; upload.setCustomValidity(error.textContent); return;
        }
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        objectUrl = URL.createObjectURL(file); document.getElementById('posterPreview').src = objectUrl;
    });
})();
