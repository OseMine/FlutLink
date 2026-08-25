/**
 * FlutCloud admin settings page logic: categories, share-category
 * assignment, subfolder locks. Talks to the existing OCS admin endpoints;
 * reloads the page after every successful mutation for a consistent view.
 */
(function () {
    'use strict';

    function apiUrl(path) {
        return OC.generateUrl('/apps/flutcloud/api/v1/public' + path);
    }

    function ocs(method, path, body) {
        const opts = {
            method: method,
            headers: {
                'OCS-APIRequest': 'true',
                'requesttoken': OC.requestToken
            },
            credentials: 'same-origin'
        };
        if (body) {
            opts.headers['Content-Type'] = 'application/x-www-form-urlencoded';
            opts.body = new URLSearchParams(body).toString();
        }
        return fetch(apiUrl(path), opts).then(function (res) {
            return res.text().then(function (text) {
                if (!res.ok) {
                    throw new Error(text.slice(0, 300));
                }
                try { return JSON.parse(text); } catch (e) { return {}; }
            });
        });
    }

    let pending = false;

    function run(promise, okMessage) {
        if (pending) { return; }
        pending = true;
        msg('');
        promise.then(function () {
            if (okMessage) { OC.Notification.showTemporary(okMessage); }
            window.location.reload();
        }).catch(function (err) {
            pending = false;
            console.error('[flutcloud]', err);
            msg(t('flutcloud', 'Fehler') + ': ' + err.message);
        });
    }

    function msg(text) {
        const el = document.getElementById('fc-msg');
        if (el) { el.textContent = text; }
    }

    document.addEventListener('DOMContentLoaded', function () {
        // --- categories -------------------------------------------------
        document.querySelectorAll('#fc-categories tbody tr').forEach(function (row) {
            const name = row.getAttribute('data-name');
            row.querySelector('.fc-prefixless').addEventListener('change', function (e) {
                run(ocs('POST', '/categories', { name: name, prefixless: e.target.checked ? 'true' : 'false' }));
            });
            row.querySelector('.fc-delete-cat').addEventListener('click', function () {
                if (!window.confirm(t('flutcloud', "Kategorie '{name}' löschen?").replace('{name}', name))) { return; }
                run(ocs('DELETE', '/categories/' + encodeURIComponent(name)));
            });
        });

        document.getElementById('fc-create-cat').addEventListener('submit', function (e) {
            e.preventDefault();
            const name = document.getElementById('fc-new-cat-name').value.trim();
            if (!name) { return; }
            const prefixless = document.getElementById('fc-new-cat-prefixless').checked ? 'true' : 'false';
            run(ocs('POST', '/categories', { name: name, prefixless: prefixless }));
        });

        // --- share assignment --------------------------------------------
        document.querySelectorAll('#fc-shares tbody tr[data-token]').forEach(function (row) {
            const token = row.getAttribute('data-token');

            row.querySelector('.fc-share-category').addEventListener('change', function (e) {
                const cat = e.target.value;
                if (cat === '') {
                    run(ocs('DELETE', '/shares/' + encodeURIComponent(token) + '/category'));
                } else {
                    run(ocs('POST', '/shares/' + encodeURIComponent(token) + '/category', { category: cat }));
                }
            });

            // --- locks ----------------------------------------------------
            const panel = row.querySelector('.fc-locks');
            const countEl = row.querySelector('.fc-lock-count');
            const list = row.querySelector('.fc-lock-list');
            let locksLoaded = false;

            function renderLocks(locks) {
                list.innerHTML = '';
                countEl.textContent = locks.length > 0 ? '(' + locks.length + ')' : '';
                locks.forEach(function (lockPath) {
                    const li = document.createElement('li');
                    const code = document.createElement('code');
                    code.textContent = lockPath;
                    const btn = document.createElement('button');
                    btn.className = 'icon-delete';
                    btn.title = t('flutcloud', 'Sperre entfernen');
                    btn.addEventListener('click', function () {
                        run(ocs('DELETE', '/shares/' + encodeURIComponent(token) + '/lock', { path: lockPath }));
                    });
                    li.appendChild(code);
                    li.appendChild(btn);
                    list.appendChild(li);
                });
            }

            function loadLocks() {
                ocs('GET', '/shares/' + encodeURIComponent(token) + '/locks').then(function (data) {
                    renderLocks((data && data.ocs && data.ocs.data && data.ocs.data.locks) || []);
                }).catch(function (err) {
                    console.error('[flutcloud]', err);
                    msg(t('flutcloud', 'Sperren konnten nicht geladen werden.'));
                });
            }

            row.querySelector('.fc-toggle-locks').addEventListener('click', function () {
                panel.hidden = !panel.hidden;
                if (!panel.hidden && !locksLoaded) {
                    locksLoaded = true;
                    loadLocks();
                }
            });

            row.querySelector('.fc-add-lock').addEventListener('submit', function (e) {
                e.preventDefault();
                const input = row.querySelector('.fc-lock-path');
                const lockPath = input.value.trim();
                if (!lockPath) { return; }
                run(ocs('POST', '/shares/' + encodeURIComponent(token) + '/lock', { path: lockPath }));
            });
        });
    });
})();
