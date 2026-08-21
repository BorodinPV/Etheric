/* Etheric Admin Console — theme handling.
   Loaded synchronously from <head> so the theme is applied before first paint. */
(function () {
    var STORAGE_KEY = 'etheric-admin-theme';
    var root = document.documentElement;

    function readStoredTheme() {
        try {
            return window.localStorage.getItem(STORAGE_KEY);
        } catch (e) {
            return null;
        }
    }

    function preferredTheme() {
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches
            ? 'light'
            : 'dark';
    }

    root.setAttribute('data-theme', readStoredTheme() || preferredTheme());

    document.addEventListener('DOMContentLoaded', function () {
        var toggle = document.querySelector('[data-theme-toggle]');
        if (!toggle) {
            return;
        }
        toggle.addEventListener('click', function () {
            var next = root.getAttribute('data-theme') === 'light' ? 'dark' : 'light';
            root.setAttribute('data-theme', next);
            try {
                window.localStorage.setItem(STORAGE_KEY, next);
            } catch (e) {
                /* storage unavailable: theme applies for this page only */
            }
        });
    });
})();
