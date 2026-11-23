(() => {
    const tabList = document.querySelector('[data-offer-tabs]');
    const tabs = [...tabList.querySelectorAll('a')];
    tabList.setAttribute('role', 'tablist');
    for (const tab of tabs) {
        const panel = document.getElementById(tab.hash.slice(1));
        tab.setAttribute('role', 'tab');
        tab.setAttribute('aria-controls', panel.id);
        panel.setAttribute('role', 'tabpanel');
        panel.setAttribute('aria-labelledby', tab.id);
        panel.tabIndex = 0;
    }

    function selectTab() {
        const selected = tabs.find(tab => tab.hash === location.hash) ?? tabs[0];
        for (const tab of tabs) {
            const active = tab === selected;
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
            document.getElementById(tab.hash.slice(1)).hidden = !active;
        }
    }

    tabList.addEventListener('click', event => {
        const tab = event.target.closest('[role=tab]');
        if (!tab || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        if (location.hash !== tab.hash) history.pushState(null, '', tab.hash);
        selectTab();
    });
    tabList.addEventListener('keydown', event => {
        const index = tabs.indexOf(document.activeElement);
        if (index < 0) return;
        const step = { ArrowLeft: -1, ArrowRight: 1 }[event.key];
        const target = event.key === 'Home' ? tabs[0] : event.key === 'End' ? tabs.at(-1)
            : step ? tabs[(index + step + tabs.length) % tabs.length] : null;
        if (!target) return;
        event.preventDefault();
        target.focus();
        target.click();
    });
    window.addEventListener('hashchange', selectTab);
    selectTab();
})();
