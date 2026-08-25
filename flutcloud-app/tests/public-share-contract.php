<?php

declare(strict_types=1);

/**
 * Contract-Test für den Gast-Zugriff auf vollständig öffentliche Shares
 * (kein Nextcloud-Server nötig).
 *
 * Geprüft werden die sicherheitskritischen Regeln aus
 * PublicShareService ohne Live-Server:
 * - Pfad-Normalisierung weist ".." ab und canonicalisiert Pfade
 * - Sperren wirken rekursiv (Ordner + alles darunter ist für Gäste weg,
 *   inklusive direktem Pfad-Zugriff), ohne Nachbarn zu treffen
 * - Kategorien: Anlegen/Löschen, Prefixless-Flag, Zuweisung unbekannter
 *   Kategorien wird abgelehnt
 *
 * Ausführen: php flutcloud-app/tests/public-share-contract.php
 */

namespace OCP {
    interface IConfig
    {
        public function getAppValue(string $appId, string $key, string $default = ''): string;
        public function setAppValue(string $appId, string $key, string $value): void;
        public function deleteAppValue(string $appId, string $key): void;
    }

    interface IUserManager
    {
        public function getDisplayName(string $uid): ?string;
    }

    interface IURLGenerator
    {
        public function getAbsoluteURL(string $path): string;
    }

    interface IShareManagerStub
    {
    }
}

namespace OCP\AppFramework {
    class App
    {
        public function __construct(string $appName, array $urlParams = [])
        {
        }
    }
}

namespace OCP\AppFramework\Bootstrap {
    interface IBootstrap
    {
        public function register(\OCP\AppFramework\Bootstrap\IRegistrationContext $context): void;

        public function boot(\OCP\AppFramework\Bootstrap\IBootContext $context): void;
    }

    interface IBootContext
    {
    }

    interface IRegistrationContext
    {
    }
}

namespace OCP\Share {
    interface IManager
    {
        /**
         * @return iterable<\OCP\Share\IShare>
         */
        public function getAllShares(): iterable;
    }

    interface IShare
    {
    }
}

namespace OCP\Files {
    interface Node
    {
    }

    class NotFoundException extends \Exception
    {
    }

    interface Folder extends Node
    {
    }
}

namespace Sabre\DAV {
    /** Minimale Sabre-Stubs (auf dem Server sind die echten Pakete aktiv). */
    class ServerPlugin
    {
        public function initialize(\Sabre\DAV\Server $server): void
        {
        }
    }

    class Server
    {
    }
}

namespace Sabre\HTTP {
    interface RequestInterface
    {
        public function getPath(): string;
    }
}

namespace {

    use OCA\FlutCloud\AppInfo\Application;
    use OCA\FlutCloud\Exception\InvalidNameException;
    use OCA\FlutCloud\Service\PublicShareService;

    require __DIR__ . '/../lib/AppInfo/Application.php';
    require __DIR__ . '/../lib/Exception/InvalidNameException.php';
    require __DIR__ . '/../lib/Service/PublicShareService.php';

    $fail = static function (string $message): never {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    };

    // Minimaler IConfig-Ersatz mit In-Memory-Speicher.
    $store = [];
    $config = new class ($store) implements \OCP\IConfig {
        public function __construct(private array &$store)
        {
        }

        public function getAppValue(string $appId, string $key, string $default = ''): string
        {
            return $this->store["$appId|$key"] ?? $default;
        }

        public function setAppValue(string $appId, string $key, string $value): void
        {
            $this->store["$appId|$key"] = $value;
        }

        public function deleteAppValue(string $appId, string $key): void
        {
            unset($this->store["$appId|$key"]);
        }
    };

    $userManager = new class implements \OCP\IUserManager {
        public function getDisplayName(string $uid): ?string
        {
            return null;
        }
    };

    $urlGenerator = new class implements \OCP\IURLGenerator {
        public function getAbsoluteURL(string $path): string
        {
            return 'https://flutcloud.example' . $path;
        }
    };

    $shareManager = new class implements \OCP\Share\IManager {
        public function getAllShares(): iterable
        {
            return [];
        }
    };

    $service = new PublicShareService($shareManager, $config, $userManager, $urlGenerator);

    // 1) Sperren: rekursiv, exakt begrenzt.
    $service->lock('tok123', '/a/b');
    $service->lock('tok123', '/top');

    foreach (['/a/b', '/a/b', '/a/b/file.txt', '/a/b/deep/inner'] as $lockedPath) {
        if (!$service->isLocked('tok123', $lockedPath)) {
            $fail("'$lockedPath' muss gesperrt sein (rekursiv).");
        }
    }
    foreach (['/', '', '/a', '/other', '/a/bc', '/a/b2/x', '/topper', '/Top'] as $openPath) {
        if ($service->isLocked('tok123', $openPath)) {
            $fail("'$openPath' darf nicht als gesperrt gelten.");
        }
    }

    // 1b) Unlock hebt nur die eigene Sperre auf.
    $service->unlock('tok123', '/a/b');
    if (!$service->isLocked('tok123', '/top') || $service->isLocked('tok123', '/a/b')) {
        $fail('unlock() darf nur die eigene Sperre entfernen.');
    }

    // 1c) Die Wurzel eines Shares lässt sich nicht sperren.
    try {
        $service->lock('tok123', '/');
        $fail('Sperren der Share-Wurzel muss abgelehnt werden.');
    } catch (InvalidNameException $e) {
        // erwartet
    }

    // 2) Pfad-Normalisierung: private Methode per Reflection prüfen.
    $normalize = new ReflectionMethod($service, 'normalizeRelPath');
    $normalize->setAccessible(true);
    $cases = [
        '' => '/',
        '/' => '/',
        'a' => '/a',
        '/a/' => '/a',
        '//a///b//' => '/a/b',
        './a/./b/.' => '/a/b',
    ];
    foreach ($cases as $input => $expected) {
        $actual = $normalize->invoke($service, $input);
        if ($actual !== $expected) {
            $fail("normalizeRelPath('$input') ergab '$actual', erwartet '$expected'.");
        }
    }
    foreach (['..', '/a/../b', '/..'] as $evil) {
        try {
            $normalize->invoke($service, $evil);
            $fail("normalizeRelPath('$evil') muss InvalidNameException werfen.");
        } catch (InvalidNameException $e) {
            // erwartet
        }
    }

    // 3) Kategorien: Anlegen mit Prefixless-Flag, Löschen räumt Zuweisungen weg.
    $service->setCategory('media', true);
    $service->setCategory('docs', false);
    $categories = $service->getCategories();
    if (($categories['media']['prefixless'] ?? null) !== true) {
        $fail("Kategorie 'media' muss prefixless=true melden.");
    }
    if (($categories['docs']['prefixless'] ?? null) !== false) {
        $fail("Kategorie 'docs' muss prefixless=false melden.");
    }

    try {
        $service->assignShare('tok123', 'does-not-exist');
        $fail('Zuweisung einer unbekannten Kategorie muss abgelehnt werden.');
    } catch (InvalidNameException $e) {
        // erwartet
    }

    try {
        $service->setCategory('  ', true);
        $fail('Leere Kategorienamen müssen abgelehnt werden.');
    } catch (InvalidNameException $e) {
        // erwartet
    }

    $config->setAppValue(Application::APP_ID, 'public_share_assignments', '{"tok9":"docs"}');
    $service->deleteCategory('docs');
    $service->deleteCategory('media');
    if ($service->getCategories() !== []) {
        $fail('Nach dem Löschen dürfen keine Kategorien übrig bleiben.');
    }
    if ($service->getShareCategory('tok9') !== null) {
        $fail('deleteCategory() muss Zuweisungen der Kategorie entfernen.');
    }

    // 4) Sabre-Plugin: nur /public.php/webdav/<token>/… wird bewacht.
    require __DIR__ . '/../lib/Dav/GuestLockPlugin.php';
    $plugin = new \OCA\FlutCloud\Dav\GuestLockPlugin($service);
    $split = new ReflectionMethod($plugin, 'splitPublicDavPath');
    $split->setAccessible(true);
    $davCases = [
        ['/public.php/webdav/tok123/a/b.txt', ['tok123', '/a/b.txt']],
        ['/public.php/webdav/tok123', ['tok123', '/']],
        ['/public.php/webdav/tok123/', ['tok123', '/']],
        ['/remote.php/dav/files/alice/x', [null, '']],
        ['/public.php/webdav', [null, '']],
    ];
    foreach ($davCases as [$input, $expected]) {
        $actual = $split->invoke($plugin, $input);
        if ($actual !== $expected) {
            $fail("splitPublicDavPath('$input') ergab "
                . var_export($actual, true) . ', erwartet ' . var_export($expected, true) . '.');
        }
    }

    echo "OK: Public-Share-Vertrag erfüllt (Pfad-Normalisierung, rekursive Sperren, Kategorien, DAV-Wache).\n";
}
