<?php
/**
 * FlutCloud admin settings — Einstellungen → Verwaltung → FlutCloud.
 * Web counterpart of the desktop/mobile admin UI: categories, share
 * assignment and subfolder locks. Mutations go through the existing OCS
 * admin endpoints; the page simply reloads afterwards.
 *
 * Template variables (from FlutCloudAdmin settings class):
 *   $categories — array of {name: string, prefixless: bool}
 *   $shares     — array of complete public shares (token, name, owner,
 *                 ownerDisplay, category, …)
 */
script('flutcloud', 'flutcloud-admin');
style('flutcloud', 'flutcloud-admin');
?>
<div id="flutcloud-admin" class="section">
    <h2><?php p($l->t('FlutCloud — öffentliche Freigaben')); ?></h2>
    <p class="settings-hint">
        Kategorien gruppieren vollständig öffentliche Freigaben
        (/apps/flutcloud/public/&lt;kategorie&gt;). Sperren blenden Unterordner
        für Gäste rekursiv aus.
    </p>

    <h3><?php p($l->t('Kategorien')); ?></h3>
    <p class="settings-hint">
        Öffentliche Kategorien werden in der Browserview angezeigt.
        Link-only-Kategorien sind nur über den direkten Link erreichbar
        und erscheinen nicht in der Übersicht.
    </p>
    <table id="fc-categories" class="grid">
        <thead>
            <tr>
                <th><?php p($l->t('Name')); ?></th>
                <th><?php p($l->t('Ohne /public/-Präfix')); ?></th>
                <th><?php p($l->t('Sichtbarkeit')); ?></th>
                <th></th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($_['categories'] as $cat): ?>
            <tr data-name="<?= htmlspecialchars($cat['name']) ?>">
                <td><?= htmlspecialchars($cat['name']) ?></td>
                <td><input type="checkbox" class="fc-prefixless"
                           <?= $cat['prefixless'] ? 'checked' : '' ?>></td>
                <td>
                    <select class="fc-visibility">
                        <option value="public"
                            <?= ($cat['visibility'] ?? 'public') === 'public' ? 'selected' : '' ?>>
                            <?php p($l->t('Öffentlich')); ?>
                        </option>
                        <option value="link-only"
                            <?= ($cat['visibility'] ?? 'public') === 'link-only' ? 'selected' : '' ?>>
                            <?php p($l->t('Link-only')); ?>
                        </option>
                    </select>
                </td>
                <td><button class="icon-delete fc-delete-cat" title="<?php p($l->t('Kategorie löschen')); ?>"></button></td>
            </tr>
            <?php endforeach; ?>
        </tbody>
    </table>
    <form id="fc-create-cat" class="fc-row">
        <input type="text" id="fc-new-cat-name"
               placeholder="<?php p($l->t('Neuer Kategoriename')); ?>" required>
        <label>
            <input type="checkbox" id="fc-new-cat-prefixless">
            <?php p($l->t('Ohne Präfix')); ?>
        </label>
        <label>
            <select id="fc-new-cat-visibility">
                <option value="public"><?php p($l->t('Öffentlich')); ?></option>
                <option value="link-only"><?php p($l->t('Link-only')); ?></option>
            </select>
        </label>
        <button type="submit" class="primary"><?php p($l->t('Kategorie erstellen')); ?></button>
    </form>

    <h3><?php p($l->t('Öffentliche Freigaben')); ?></h3>
    <p class="settings-hint">
        Kategorie zuweisen und Unterordner für Gäste sperren. Die Sperrenliste
        lädt beim Aufklappen.
    </p>
    <table id="fc-shares" class="grid">
        <thead>
            <tr>
                <th><?php p($l->t('Freigabe')); ?></th>
                <th><?php p($l->t('Kategorie')); ?></th>
                <th><?php p($l->t('Sperren')); ?></th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($_['shares'] as $share): ?>
            <tr data-token="<?= htmlspecialchars($share['token']) ?>">
                <td>
                    <strong><?= htmlspecialchars($share['name']) ?></strong><br>
                    <span class="fc-muted"><?= htmlspecialchars($share['ownerDisplay'] ?? $share['owner']) ?></span>
                </td>
                <td>
                    <select class="fc-share-category">
                        <option value="">— <?php p($l->t('keine')); ?> —</option>
                        <?php foreach ($_['categories'] as $cat): ?>
                        <option value="<?= htmlspecialchars($cat['name']) ?>"
                            <?= $share['category'] === $cat['name'] ? 'selected' : '' ?>>
                            <?= htmlspecialchars($cat['name']) ?>
                        </option>
                        <?php endforeach; ?>
                    </select>
                </td>
                <td>
                    <button class="fc-toggle-locks"><?php p($l->t('Sperren verwalten')); ?></button>
                    <span class="fc-lock-count fc-muted"></span>
                    <div class="fc-locks" hidden>
                        <ul class="fc-lock-list"></ul>
                        <form class="fc-add-lock fc-row">
                            <input type="text" class="fc-lock-path"
                                   placeholder="<?php p($l->t('Ordnerpfad, z. B. /Unterordner')); ?>" required>
                            <button type="submit"><?php p($l->t('Ordner sperren')); ?></button>
                        </form>
                    </div>
                </td>
            </tr>
            <?php endforeach; ?>
            <?php if (count($_['shares']) === 0): ?>
            <tr><td colspan="3"><?php p($l->t('Keine öffentlichen Freigaben vorhanden.')); ?></td></tr>
            <?php endif; ?>
        </tbody>
    </table>

    <div id="fc-msg" role="status"></div>
</div>
