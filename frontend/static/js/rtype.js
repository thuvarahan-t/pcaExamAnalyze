/*
 * Modern glass dropdown for a resource's type (Files / Videos / Practical).
 * Replaces the native <select> in each resource row of the cell editor.
 *
 * Rows are cloned from a <template> at runtime, so behaviour is wired with one
 * delegated document listener. cell-modal.js sets a row's value via window.setRType.
 *
 * The open panel is PORTALED to <body> and positioned as a fixed popover (flips
 * upward when there's no room below). This is required because the modal card has a
 * backdrop-filter (glass), which would otherwise clip an in-place dropdown.
 */
(function () {
    "use strict";

    var openRoot = null;   // the .rtype whose panel is currently shown
    var openPanel = null;  // its .rtype-panel, temporarily moved to <body>

    function closeDropdown() {
        if (openRoot && openPanel) {
            openRoot.classList.remove("open");
            openPanel.classList.remove("open");
            openPanel.removeAttribute("style");
            openRoot.appendChild(openPanel); // move the panel back into its row
        }
        openRoot = null;
        openPanel = null;
    }

    function openDropdown(root) {
        closeDropdown();
        var panel = root.querySelector(".rtype-panel");
        if (!panel) return;
        openRoot = root;
        openPanel = panel;
        document.body.appendChild(panel);   // portal out of the clipping modal
        root.classList.add("open");
        panel.classList.add("open");
        positionPanel(root, panel);
    }

    // Place the panel right under (or above) its trigger, in viewport coords.
    function positionPanel(root, panel) {
        var trigger = root.querySelector(".rtype-trigger");
        if (!trigger) return;
        var r = trigger.getBoundingClientRect();
        var gap = 6;
        panel.style.width = r.width + "px";
        panel.style.left = r.left + "px";
        panel.style.top = "-9999px";        // measure off-screen first
        var ph = panel.offsetHeight;
        var spaceBelow = window.innerHeight - r.bottom;
        if (spaceBelow < ph + gap && r.top > ph + gap) {
            panel.style.top = (r.top - ph - gap) + "px"; // flip upward
        } else {
            panel.style.top = (r.bottom + gap) + "px";
        }
    }

    // Sync the hidden input and mirror the chosen option's icon + label into the trigger.
    function applyValue(root, value) {
        var hidden = root.querySelector('input[name="refType"]');
        // The panel may be portaled to <body>, so search it relative to the root too.
        var panel = (openRoot === root && openPanel) ? openPanel : root.querySelector(".rtype-panel");
        var opt = value && panel.querySelector('.rtype-option[data-value="' + value + '"]');
        if (!opt) opt = panel.querySelector(".rtype-option");
        if (!opt || !hidden) return;
        hidden.value = opt.dataset.value;
        root.querySelector(".rtype-current").innerHTML = opt.innerHTML;
    }

    window.setRType = applyValue;

    document.addEventListener("click", function (e) {
        var trigger = e.target.closest(".rtype-trigger");
        if (trigger) {
            e.preventDefault();
            var root = trigger.closest(".rtype");
            if (openRoot === root) { closeDropdown(); } else { openDropdown(root); }
            return;
        }

        var option = e.target.closest(".rtype-option");
        if (option && openRoot) {
            e.preventDefault();
            applyValue(openRoot, option.dataset.value);
            closeDropdown();
            return;
        }

        // Clicks inside the open panel (but not on an option) should not close it.
        if (e.target.closest(".rtype-panel")) return;

        closeDropdown();
    });

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") closeDropdown();
    });

    // A fixed popover can't follow scroll/resize — close it instead of letting it drift.
    window.addEventListener("resize", closeDropdown);
    document.addEventListener("scroll", closeDropdown, true);
})();
