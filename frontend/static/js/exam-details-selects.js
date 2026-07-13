(function () {
    var COLOURS = ['#c9b8ff', '#b9e7f5', '#ffd3dc', '#c8f1df', '#ffd7bb', '#badcff'];

    function initSelect(name, placeholder) {
        var box = document.getElementById(name + '-select');
        var hidden = document.getElementById(name);
        var trigger = document.getElementById(name + '-trigger');
        var triggerText = document.getElementById(name + '-trigger-text');
        var panel = document.getElementById(name + '-panel');
        var search = document.getElementById(name + '-search');
        var options = box ? Array.prototype.slice.call(box.querySelectorAll('.gselect-option')) : [];
        if (!box || !hidden || !trigger || !triggerText || !panel) { return; }

        options.forEach(function (option, index) {
            option.style.setProperty('--dot', COLOURS[index % COLOURS.length]);
        });

        function setValue(value) {
            hidden.value = value;
            triggerText.textContent = value || placeholder;
            triggerText.classList.toggle('gselect-placeholder', !value);
            options.forEach(function (option) {
                var active = option.getAttribute('data-value') === value;
                option.classList.toggle('active', active);
                option.setAttribute('aria-selected', active ? 'true' : 'false');
            });
        }

        function filter(value) {
            var query = value.trim().toLowerCase();
            options.forEach(function (option) {
                var match = option.getAttribute('data-value').toLowerCase().indexOf(query) !== -1;
                option.classList.toggle('is-hidden', !match);
            });
        }

        function placePanel() {
            panel.hidden = false;
            var panelRect = panel.getBoundingClientRect();
            var triggerRect = trigger.getBoundingClientRect();
            var viewportWidth = document.documentElement.clientWidth || window.innerWidth;
            var viewportHeight = document.documentElement.clientHeight || window.innerHeight;
            var left = Math.max(12, Math.min(triggerRect.left, viewportWidth - panelRect.width - 12));
            var top = triggerRect.bottom + 7;
            if (top + panelRect.height > viewportHeight - 12) {
                top = Math.max(12, triggerRect.top - panelRect.height - 7);
            }
            panel.style.left = Math.round(left) + 'px';
            panel.style.right = 'auto';
            panel.style.top = Math.round(top) + 'px';
        }

        function openPanel(open) {
            box.classList.toggle('open', open);
            panel.hidden = !open;
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
            if (open) {
                if (search) { search.value = ''; filter(''); }
                placePanel();
                if (search) { search.focus(); }
            }
        }

        options.forEach(function (option) {
            option.addEventListener('click', function () {
                setValue(option.getAttribute('data-value'));
                openPanel(false);
            });
        });
        trigger.addEventListener('click', function () { openPanel(panel.hidden); });
        if (search) { search.addEventListener('input', function () { filter(search.value); }); }
        document.addEventListener('click', function (event) {
            if (!box.contains(event.target)) { openPanel(false); }
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') { openPanel(false); }
        });
        window.addEventListener('resize', function () {
            if (!panel.hidden) { placePanel(); }
        }, { passive: true });

        setValue(hidden.value);
    }

    function init() {
        initSelect('batch', 'Select your batch');
        initSelect('stream', 'Select your stream');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
