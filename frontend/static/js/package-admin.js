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
    function setupStudentPages(filterId, rowSelector, listName) {
        const filter = document.getElementById(filterId);
        if (!filter) return;
        const rows = [...document.querySelectorAll(rowSelector)];
        const pagination = document.querySelector('[data-student-pagination="' + listName + '"]');
        const previous = pagination?.querySelector('[data-page-previous]');
        const next = pagination?.querySelector('[data-page-next]');
        const pageStatus = pagination?.querySelector('[data-page-status]');
        const searchStatus = document.getElementById(filterId + 'Status');
        const pageSize = 10;
        let page = 1;

        function render() {
            const query = filter.value.trim().toLocaleLowerCase();
            const matches = rows.filter(row => {
                const text = row.cells[0].textContent + ' ' + row.cells[1].textContent;
                return text.toLocaleLowerCase().includes(query);
            });
            const totalPages = Math.max(1, Math.ceil(matches.length / pageSize));
            page = Math.min(page, totalPages);
            rows.forEach(row => { row.hidden = true; });
            matches.slice((page - 1) * pageSize, page * pageSize).forEach(row => { row.hidden = false; });
            searchStatus.textContent = matches.length ? matches.length + ' students' : 'No matching students';
            if (pageStatus) pageStatus.textContent = 'Page ' + page + ' of ' + totalPages;
            if (previous) previous.disabled = page === 1;
            if (next) next.disabled = page === totalPages;
            if (pagination) pagination.hidden = matches.length === 0;
        }

        filter.addEventListener('input', () => { page = 1; render(); });
        previous?.addEventListener('click', () => { page = Math.max(1, page - 1); render(); });
        next?.addEventListener('click', () => { page++; render(); });
        render();
    }
    setupStudentPages('purchaseFilter', '[data-purchase-row]', 'purchases');
    setupStudentPages('interestFilter', '[data-interest-row]', 'interested');
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
