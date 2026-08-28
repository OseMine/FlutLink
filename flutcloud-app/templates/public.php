<?php
/**
 * Public shares landing page — rendered server-side for guests who open
 * /apps/flutcloud/public or /public/<category> in a browser.
 *
 * Template variables (from PublicPagesController):
 *   $category   — active category string or null (all)
 *   $categories — all configured categories (array of {name, prefixless})
 *   $shares     — filtered list of public shares (array of share dicts)
 *   $publicBase — absolute base URL of the guest web routes (/…/public)
 *   $adminUrl   — absolute URL of the FlutCloud admin settings page
 *   $isAdmin    — whether the current visitor is a logged-in admin
 */
header('Content-Type: text/html; charset=utf-8');
?>
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>FlutCloud — Öffentliche Freigaben</title>
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        :root {
            --bg: #0d1117;
            --surface: #161b22;
            --surface-hover: #1c2333;
            --border: #30363d;
            --text: #e6edf3;
            --text-secondary: #8b949e;
            --primary: #58a6ff;
            --primary-bg: rgba(88,166,255,.12);
            --chip-bg: #21262d;
            --chip-active-bg: var(--primary-bg);
            --chip-active-border: var(--primary);
            --card-radius: 12px;
            --font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }

        body {
            font-family: var(--font);
            background: var(--bg);
            color: var(--text);
            line-height: 1.5;
            min-height: 100vh;
        }

        .container {
            max-width: 860px;
            margin: 0 auto;
            padding: 24px 20px 60px;
        }

        /* Header */
        .header {
            text-align: center;
            margin-bottom: 32px;
        }
        .header h1 {
            font-size: 1.5rem;
            font-weight: 600;
            margin-bottom: 4px;
        }
        .header .subtitle {
            color: var(--text-secondary);
            font-size: 0.875rem;
        }

        /* Category chips */
        .chips {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin-bottom: 24px;
            justify-content: center;
        }
        .chip {
            display: inline-block;
            padding: 6px 16px;
            border-radius: 20px;
            background: var(--chip-bg);
            border: 1px solid var(--border);
            color: var(--text-secondary);
            text-decoration: none;
            font-size: 0.8125rem;
            font-weight: 500;
            transition: background .15s, border-color .15s, color .15s;
        }
        .chip:hover {
            background: var(--surface-hover);
            color: var(--text);
        }
        .chip.active {
            background: var(--chip-active-bg);
            border-color: var(--chip-active-border);
            color: var(--primary);
        }

        /* Share cards */
        .shares {
            display: grid;
            gap: 12px;
        }
        .share-card {
            display: flex;
            align-items: center;
            gap: 14px;
            padding: 16px 20px;
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: var(--card-radius);
            text-decoration: none;
            color: inherit;
            transition: background .15s, border-color .15s;
        }
        .share-card:hover {
            background: var(--surface-hover);
            border-color: #484f58;
        }
        .share-icon {
            flex-shrink: 0;
            width: 40px; height: 40px;
            border-radius: 10px;
            background: var(--primary-bg);
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .share-icon svg {
            width: 20px; height: 20px;
            fill: var(--primary);
        }
        .share-info { flex: 1; min-width: 0; }
        .share-name {
            font-weight: 600;
            font-size: 0.9375rem;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .share-owner {
            font-size: 0.8125rem;
            color: var(--text-secondary);
        }
        .share-badge {
            flex-shrink: 0;
            padding: 3px 10px;
            border-radius: 12px;
            background: var(--primary-bg);
            color: var(--primary);
            font-size: 0.75rem;
            font-weight: 500;
        }

        .empty {
            text-align: center;
            padding: 60px 20px;
            color: var(--text-secondary);
        }

        .footer {
            text-align: center;
            margin-top: 48px;
            font-size: 0.75rem;
            color: var(--text-secondary);
        }
    </style>
</head>
<body>
<div class="container">
    <div class="header">
        <h1>FlutCloud</h1>
        <p class="subtitle">Öffentliche Freigaben<?php if ($category !== null): ?> — <?= htmlspecialchars($category) ?><?php endif; ?></p>
    </div>

    <?php if (count($categories) > 0): ?>
    <div class="chips">
        <a class="chip <?= $category === null ? 'active' : '' ?>"
           href="<?= htmlspecialchars($publicBase) ?>">Alle</a>
        <?php foreach ($categories as $cat): ?>
        <a class="chip <?= $category === $cat['name'] ? 'active' : '' ?>"
           href="<?= htmlspecialchars($publicBase . '/' . rawurlencode($cat['name'])) ?>"><?= htmlspecialchars($cat['name']) ?></a>
        <?php endforeach; ?>
    </div>
    <?php endif; ?>

    <?php if (empty($shares)): ?>
    <div class="empty">
        <p>Keine öffentlichen Freigaben vorhanden.</p>
    </div>
    <?php else: ?>
    <div class="shares">
        <?php foreach ($shares as $share): ?>
        <a class="share-card" href="<?= htmlspecialchars($share['url']) ?>" target="_blank" rel="noopener">
            <div class="share-icon">
                <svg viewBox="0 0 24 24"><path d="M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>
            </div>
            <div class="share-info">
                <div class="share-name"><?= htmlspecialchars($share['name']) ?></div>
                <div class="share-owner"><?= htmlspecialchars($share['ownerDisplay'] ?? $share['owner']) ?></div>
            </div>
            <?php if ($share['category'] !== null): ?>
            <span class="share-badge"><?= htmlspecialchars($share['category']) ?></span>
            <?php endif; ?>
        </a>
        <?php endforeach; ?>
    </div>
    <?php endif; ?>

    <div class="footer">
        Powered by <a href="https://instagram.com/marcante_musik" style="color: var(--primary); text-decoration: none;">Marcante Musik</a> &middot; <a href="https://github.com/OseMine/FlutLink" style="color: var(--primary); text-decoration: none;">FlutLink</a>
        <?php if (!empty($isAdmin)): ?>
        <br><a href="<?= htmlspecialchars($adminUrl) ?>" style="color: var(--primary); text-decoration: none;">Admin-Verwaltung (Kategorien, Sperren)</a>
        <?php endif; ?>
    </div>
</div>
</body>
</html>
