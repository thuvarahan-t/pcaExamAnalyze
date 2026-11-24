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
    function setupStudentPages(filterId, rowSelector, listName, searchableColumns) {
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
                const text = Array.from(row.cells).slice(0, searchableColumns).map(cell => cell.textContent).join(' ');
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
    setupStudentPages('purchaseFilter', '[data-purchase-row]', 'purchases', 4);
    setupStudentPages('interestFilter', '[data-interest-row]', 'interested', 2);
    const phone = document.getElementById('purchasePhone');
    if (phone) {
        const warning = document.getElementById('manualPhoneMatches');
        const list = document.getElementById('manualPhoneMatchList');
        const shared = document.getElementById('sharedPhone');
        const rows = [...document.querySelectorAll('[data-purchase-row][data-phone], [data-interest-row][data-phone]')];
        function showMatches() {
            const matches = rows.filter(row => row.dataset.phone === '07' + phone.value);
            list.replaceChildren(...matches.map(row => {
                const item = document.createElement('li');
                const enquiry = row.hasAttribute('data-interest-row');
                item.textContent = row.cells[1].textContent + ' · ' + row.cells[0].textContent.trim()
                    + ' · ' + (enquiry ? 'Enquiry' : 'Confirmed purchase');
                return item;
            }));
            warning.hidden = matches.length === 0;
            shared.checked = false;
            shared.disabled = warning.hidden;
            shared.required = !warning.hidden;
        }
        phone.addEventListener('input', showMatches);
        showMatches();
    }
    const originalPrice = document.getElementById('originalPrice');
    const offerPrice = document.getElementById('offerPrice');
    const discountPercent = document.getElementById('discountPercent');
    if (originalPrice && offerPrice && discountPercent) {
        function updateOfferPrice() {
            const hasDiscount = discountPercent.value !== '' || discountPercent.validity.badInput;
            offerPrice.readOnly = hasDiscount;
            offerPrice.required = !hasDiscount;
            if (!hasDiscount) return;
            offerPrice.value = '';
            if (!originalPrice.value || !originalPrice.validity.valid || !discountPercent.validity.valid) return;
            const originalCents = Math.round(originalPrice.valueAsNumber * 100);
            const offerCents = Math.floor((originalCents * (100 - discountPercent.valueAsNumber) + 50) / 100);
            offerPrice.value = (offerCents / 100).toFixed(2);
        }
        originalPrice.addEventListener('input', updateOfferPrice);
        discountPercent.addEventListener('input', updateOfferPrice);
        updateOfferPrice();
    }
})();
