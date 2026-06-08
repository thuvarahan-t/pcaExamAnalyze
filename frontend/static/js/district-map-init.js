/*
 * District picker for the registration page.
 *
 * Two ways to choose a district, kept in sync:
 *   - a clean custom glassmorphism dropdown (left), and
 *   - the interactive Raphael Sri Lanka map (right, from sl-district-map.js).
 *
 * Both write to the hidden #district field that the form submits. Each district gets its
 * own distinct colour, used BOTH as the map fill and as the dropdown's colour dot, so the
 * two views match. The map's stable path ids (d1..d25) map to the canonical names.
 */
(function () {
    var ID_TO_NAME = {
        d1: 'Colombo', d2: 'Gampaha', d3: 'Kalutara', d4: 'Kandy', d5: 'Matale',
        d6: 'Nuwara Eliya', d7: 'Galle', d8: 'Matara', d9: 'Hambantota', d10: 'Jaffna',
        d11: 'Kilinochchi', d12: 'Mannar', d13: 'Vavuniya', d14: 'Mullaitivu',
        d15: 'Batticaloa', d16: 'Ampara', d17: 'Trincomalee', d18: 'Kurunegala',
        d19: 'Puttalam', d20: 'Anuradhapura', d21: 'Polonnaruwa', d22: 'Badulla',
        d23: 'Monaragala', d24: 'Ratnapura', d25: 'Kegalle'
    };

    var SEL_STROKE = '#111827';   // crisp neutral outline on the selected district
    var BASE_STROKE = '#ffffff';  // crisp white seams between districts

    // Balanced island palette: cool blues/teals with warm accents for a more premium map.
    var DISTRICT_COLOURS = [
        '#2563eb', '#0ea5e9', '#06b6d4', '#14b8a6', '#22c55e',
        '#84cc16', '#f59e0b', '#f97316', '#ef4444', '#ec4899',
        '#a855f7', '#7c3aed', '#4f46e5', '#0891b2', '#0f766e',
        '#15803d', '#65a30d', '#ca8a04', '#ea580c', '#dc2626',
        '#db2777', '#9333ea', '#6366f1', '#0284c7', '#059669'
    ];

    function colourFor(index) {
        return DISTRICT_COLOURS[index % DISTRICT_COLOURS.length];
    }

    function init() {
        var districts = window.districts;
        var hidden = document.getElementById('district');
        var label = document.getElementById('district-name');
        var mapBox = document.getElementById('map');
        var box = document.getElementById('district-select');
        var trigger = document.getElementById('district-trigger');
        var triggerText = document.getElementById('district-trigger-text');
        var panel = document.getElementById('district-panel');
        var search = document.getElementById('district-search');
        var options = box ? Array.prototype.slice.call(box.querySelectorAll('.gselect-option')) : [];
        if (!districts || !districts.length || !hidden || !mapBox || !box) {
            return;
        }

        // Build name -> colour from the map order, so dropdown dots match map fills exactly.
        var nameColour = {};
        districts.forEach(function (el, i) { nameColour[ID_TO_NAME[el.data('id')]] = colourFor(i); });

        function nameOf(el) { return ID_TO_NAME[el.data('id')]; }

        function paintMap(selectedName) {
            districts.forEach(function (el) {
                var isSel = nameOf(el) === selectedName;
                el.attr({
                    stroke: isSel ? SEL_STROKE : BASE_STROKE,
                    'stroke-width': isSel ? 3 : 1.15,
                    'fill-opacity': isSel ? 1 : 0.92
                });
                if (isSel) { el.toFront(); }
            });
        }

        function setDistrict(name) {
            hidden.value = name;
            triggerText.textContent = name;
            triggerText.classList.remove('gselect-placeholder');
            if (label) { label.textContent = name; }
            options.forEach(function (li) {
                li.classList.toggle('active', li.getAttribute('data-value') === name);
            });
            paintMap(name);
        }

        function placePanel() {
            panel.classList.remove('align-right');
            var panelRect = panel.getBoundingClientRect();
            var viewportWidth = document.documentElement.clientWidth || window.innerWidth;
            if (panelRect.right > viewportWidth - 12) {
                panel.classList.add('align-right');
            }
        }

        function openPanel(open) {
            box.classList.toggle('open', open);
            panel.hidden = !open;
            trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
            if (open) {
                placePanel();
                if (search) { search.value = ''; filter(''); search.focus(); }
            }
        }

        function filter(q) {
            q = q.trim().toLowerCase();
            options.forEach(function (li) {
                var match = li.getAttribute('data-value').toLowerCase().indexOf(q) !== -1;
                li.classList.toggle('is-hidden', !match);
            });
        }

        // ---- Dropdown wiring ----
        options.forEach(function (li) {
            var name = li.getAttribute('data-value');
            li.style.setProperty('--dot', nameColour[name] || '#cbd5e1');
            li.addEventListener('click', function () { setDistrict(name); openPanel(false); });
        });
        trigger.addEventListener('click', function () { openPanel(panel.hidden); });
        if (search) { search.addEventListener('input', function () { filter(search.value); }); }
        document.addEventListener('click', function (e) {
            if (!box.contains(e.target)) { openPanel(false); }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { openPanel(false); }
        });

        // ---- Map wiring ----
        districts.forEach(function (el, i) {
            var fill = colourFor(i);
            el.attr({ fill: fill, stroke: BASE_STROKE, 'stroke-width': 1.15, 'fill-opacity': 0.92 });
            el.node.style.cursor = 'pointer';
            el.click(function () { setDistrict(nameOf(this)); });
            el.mouseover(function () {
                this.attr({ 'fill-opacity': 1, stroke: SEL_STROKE, 'stroke-width': 2.2 });
                this.toFront();
                if (label) { label.textContent = nameOf(this); }
            });
            el.mouseout(function () {
                paintMap(hidden.value);
                if (label) { label.textContent = hidden.value || 'Tap your district on the map'; }
            });
        });

        // Make the fixed-size Raphael SVG scale to its container.
        var svg = mapBox.querySelector('svg');
        if (svg) {
            var w = svg.getAttribute('width');
            var h = svg.getAttribute('height');
            if (w && h) { svg.setAttribute('viewBox', '0 0 ' + w + ' ' + h); }
            svg.removeAttribute('width');
            svg.removeAttribute('height');
            svg.style.width = '100%';
            svg.style.height = 'auto';
            svg.style.display = 'block';
        }

        // Reflect any pre-selected value (e.g. after a validation-error re-render).
        if (hidden.value) { setDistrict(hidden.value); }
        else { paintMap(null); if (label) { label.textContent = 'Tap your district on the map'; } }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
