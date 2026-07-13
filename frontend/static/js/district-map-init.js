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

    var NAME_TO_PROVINCE = {
        Colombo: 'Western', Gampaha: 'Western', Kalutara: 'Western',
        Kandy: 'Central', Matale: 'Central', 'Nuwara Eliya': 'Central',
        Galle: 'Southern', Matara: 'Southern', Hambantota: 'Southern',
        Jaffna: 'Northern', Kilinochchi: 'Northern', Mannar: 'Northern', Vavuniya: 'Northern', Mullaitivu: 'Northern',
        Batticaloa: 'Eastern', Ampara: 'Eastern', Trincomalee: 'Eastern',
        Kurunegala: 'North Western', Puttalam: 'North Western',
        Anuradhapura: 'North Central', Polonnaruwa: 'North Central',
        Badulla: 'Uva', Monaragala: 'Uva',
        Ratnapura: 'Sabaragamuwa', Kegalle: 'Sabaragamuwa'
    };

    var SELECTED_FILL = '#e75b99';
    var SELECTED_STROKE = '#d81b72';
    var HOVER_STROKE = '#f472b6';

    // Soft reference-style pastels: lavender, peach, mint, aqua, blue, and blush.
    var DISTRICT_COLOURS = [
        '#c9b8ff', '#d8caff', '#b9e7f5', '#f5ddc8', '#ffd3dc',
        '#c8f1df', '#bfe3ff', '#ffd9cb', '#d9efbf', '#d9c9ff',
        '#c3eaf7', '#d7f4db', '#b8d6ff', '#f2c8d9', '#ffd7bb',
        '#f6c5d5', '#c4efe9', '#e8d6c2', '#b7dcff', '#c9f0cf',
        '#eed5ff', '#c0efe0', '#f6d7dd', '#d2efc5', '#badcff'
    ];
    var DISTRICT_STROKES = [
        '#aa92f6', '#b4a0f7', '#7ccae1', '#e5b47f', '#ee97aa',
        '#7fd8b7', '#77bcea', '#efa794', '#abd68d', '#ae9df1',
        '#79c9dd', '#9bdda7', '#7ba9ea', '#e08bae', '#efa982',
        '#de7ba4', '#83d8cc', '#d5b27f', '#7bb6ee', '#9fd387',
        '#c29ee8', '#82d8bc', '#e59aaa', '#a2d489', '#80b2e6'
    ];

    function colourFor(index) {
        return DISTRICT_COLOURS[index % DISTRICT_COLOURS.length];
    }
    function strokeFor(index) {
        return DISTRICT_STROKES[index % DISTRICT_STROKES.length];
    }

    // On phones we show the dropdown only — the map is hidden (CSS) and not worth wiring up.
    function mapEnabled() {
        return !window.matchMedia('(max-width: 820px)').matches;
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
        var useMap = mapEnabled();

        // Build name -> colour from the map order, so dropdown dots match map fills exactly.
        var nameColour = {};
        var nameStroke = {};
        districts.forEach(function (el, i) {
            nameColour[ID_TO_NAME[el.data('id')]] = colourFor(i);
            nameStroke[ID_TO_NAME[el.data('id')]] = strokeFor(i);
        });

        function nameOf(el) { return ID_TO_NAME[el.data('id')]; }
        function labelFor(name) {
            return name ? name + ' - ' + NAME_TO_PROVINCE[name] : 'Tap your district';
        }
        function optionFor(name) {
            for (var i = 0; i < options.length; i++) {
                if (options[i].getAttribute('data-value') === name) { return options[i]; }
            }
            return null;
        }
        function clearHoverOption() {
            options.forEach(function (li) { li.classList.remove('hover-match'); });
        }
        function previewDistrict(name) {
            clearHoverOption();
            if (search && search.value) {
                search.value = '';
                filter('');
            }
            var li = optionFor(name);
            if (li) {
                li.classList.add('hover-match');
                li.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
            }
            if (label) { label.textContent = labelFor(name); }
        }

        function paintMap(selectedName) {
            if (!useMap) { return; }
            districts.forEach(function (el) {
                var name = nameOf(el);
                var isSel = name === selectedName;
                el.attr({
                    fill: isSel ? SELECTED_FILL : nameColour[name],
                    stroke: isSel ? SELECTED_STROKE : nameStroke[name],
                    'stroke-width': isSel ? 2.8 : 1.1,
                    'fill-opacity': isSel ? 1 : 0.84
                });
                el.node.classList.toggle('is-selected', isSel);
                el.node.setAttribute('aria-pressed', isSel ? 'true' : 'false');
                el.node.style.filter = isSel ? 'drop-shadow(0 4px 10px rgba(216,27,114,.42))' : '';
                if (isSel) { el.toFront(); }
            });
        }

        function setDistrict(name) {
            hidden.value = name;
            triggerText.textContent = name;
            triggerText.classList.remove('gselect-placeholder');
            if (label) { label.textContent = labelFor(name); }
            options.forEach(function (li) {
                var isActive = li.getAttribute('data-value') === name;
                li.classList.toggle('active', isActive);
                li.setAttribute('aria-selected', isActive ? 'true' : 'false');
            });
            paintMap(name);
            var activeOption = optionFor(name);
            if (activeOption) { activeOption.scrollIntoView({ block: 'nearest', behavior: 'smooth' }); }
        }

        function placePanel() {
            panel.classList.remove('align-right');
            var panelRect = panel.getBoundingClientRect();
            var viewportWidth = document.documentElement.clientWidth || window.innerWidth;
            var viewportHeight = document.documentElement.clientHeight || window.innerHeight;
            if (window.getComputedStyle(panel).position === 'fixed') {
                var triggerRect = trigger.getBoundingClientRect();
                var left = Math.max(12, Math.min(triggerRect.left, viewportWidth - panelRect.width - 12));
                var top = triggerRect.bottom + 7;
                if (top + panelRect.height > viewportHeight - 12) {
                    top = Math.max(12, triggerRect.top - panelRect.height - 7);
                }
                panel.style.left = Math.round(left) + 'px';
                panel.style.right = 'auto';
                panel.style.top = Math.round(top) + 'px';
                return;
            }
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
            li.addEventListener('mouseover', function () { previewDistrict(name); });
            li.addEventListener('mouseout', clearHoverOption);
        });
        trigger.addEventListener('click', function () { openPanel(panel.hidden); });
        if (search) { search.addEventListener('input', function () { filter(search.value); }); }
        document.addEventListener('click', function (e) {
            if (!box.contains(e.target)) { openPanel(false); }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') { openPanel(false); }
        });

        // ---- Map wiring (desktop/tablet only — skipped on phones) ----
        if (useMap) {
            districts.forEach(function (el, i) {
                var name = nameOf(el);
                el.attr({ fill: colourFor(i), stroke: strokeFor(i), 'stroke-width': 1.1, 'fill-opacity': 0.84 });
                el.node.style.cursor = 'pointer';
                el.node.setAttribute('role', 'button');
                el.node.setAttribute('tabindex', '0');
                el.node.setAttribute('aria-label', 'Select ' + name + ' district');
                // Tag each path with its district name so a delegated click can resolve it.
                el.node.setAttribute('data-district', name);
                el.mouseover(function () {
                    var hovered = nameOf(this);
                    var isSelected = hidden.value === hovered;
                    paintMap(hidden.value);
                    this.attr({ stroke: HOVER_STROKE, 'stroke-width': isSelected ? 3.2 : 2.6 });
                    this.node.style.filter = 'drop-shadow(0 4px 9px rgba(244,114,182,.32))';
                    this.toFront();
                    previewDistrict(hovered);
                });
                el.mouseout(function () {
                    clearHoverOption();
                    paintMap(hidden.value);
                    if (label) { label.textContent = hidden.value ? labelFor(hidden.value) : 'Tap your district'; }
                });
                el.node.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setDistrict(name);
                        clearHoverOption();
                        openPanel(false);
                    }
                });
            });

            // Delegated click on the map container. The container never moves, so this stays
            // reliable even though hovering calls toFront() and reorders the path nodes —
            // a per-path native click can get swallowed when its node is moved mid-gesture.
            var selectFromMap = function (e) {
                var node = e.target.closest('[data-district]');
                if (!node || !mapBox.contains(node)) { return; }
                e.preventDefault();
                setDistrict(node.getAttribute('data-district'));
                clearHoverOption();
                openPanel(false);
            };
            // pointerup is more reliable after Raphael moves a hovered path to the front.
            // Keep click as a fallback for browsers and keyboard-generated activation.
            if (window.PointerEvent) { mapBox.addEventListener('pointerup', selectFromMap); }
            else { mapBox.addEventListener('click', selectFromMap); }

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
        }

        // Reflect any pre-selected value (e.g. after a validation-error re-render).
        if (hidden.value) { setDistrict(hidden.value); }
        else { paintMap(null); if (label) { label.textContent = 'Tap your district'; } }

        window.addEventListener('resize', function () {
            if (!panel.hidden) { placePanel(); }
        }, { passive: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
