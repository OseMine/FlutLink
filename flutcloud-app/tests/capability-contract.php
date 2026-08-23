<?php

declare(strict_types=1);

/**
 * Capability-Contract-Test (kein Nextcloud-Server nötig).
 *
 * Der FlutLink-Client erzwingt beim Verbinden die FlutCloud-Capability
 * (src-tauri/src/flutcloud.rs, verify_server): /ocs/v2.php/cloud/
 * capabilities?format=json muss ocs.data.capabilities.flutcloud liefern.
 *
 * Dieser Test stubt die benötigten OCP-Klassen und prüft die App-Seite des
 * Vertrags: Application::APP_ID, das Capability-Payload (version + features)
 * und die Registrierung über IRegistrationContext.
 *
 * Ausführen: php flutcloud-app/tests/capability-contract.php
 */

namespace OCP\App {
    interface IAppManager
    {
        public function getAppVersion(string $appId): string;
    }
}

namespace OCP\Capabilities {
    interface ICapability
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
    interface IBootContext
    {
    }

    interface IBootstrap
    {
        public function register(IRegistrationContext $context): void;

        public function boot(IBootContext $context): void;
    }

    interface IRegistrationContext
    {
        public function registerCapability(string $capability): void;
    }
}

namespace {
    use OCA\FlutCloud\AppInfo\Application;
    use OCA\FlutCloud\Capabilities;

    require __DIR__ . '/../lib/AppInfo/Application.php';
    require __DIR__ . '/../lib/Capabilities.php';

    $fail = static function (string $message): never {
        fwrite(STDERR, "FAIL: {$message}\n");
        exit(1);
    };

    // 1) App-ID — der Client erwartet genau diese Capability-Key.
    if (Application::APP_ID !== 'flutcloud') {
        $fail('Application::APP_ID ist nicht "flutcloud".');
    }

    // 2) getCapabilities() liefert flutcloud => { version, features }.
    $appManager = new class implements \OCP\App\IAppManager {
        public function getAppVersion(string $appId): string
        {
            return '0.0.0-test';
        }
    };
    $capabilities = (new Capabilities($appManager))->getCapabilities();
    if (!isset($capabilities['flutcloud']) || !is_array($capabilities['flutcloud'])) {
        $fail('getCapabilities() liefert kein "flutcloud"-Objekt (Client-Pointer /ocs/data/capabilities/flutcloud).');
    }
    if (!isset($capabilities['flutcloud']['version']) || !is_string($capabilities['flutcloud']['version'])) {
        $fail('flutcloud.version fehlt oder ist kein String.');
    }
    if (!isset($capabilities['flutcloud']['features']) || !is_array($capabilities['flutcloud']['features'])) {
        $fail('flutcloud.features fehlt oder ist kein Array.');
    }

    // 3) register() meldet Capabilities an, damit der Endpoint sie ausliefert.
    $registration = new class implements \OCP\AppFramework\Bootstrap\IRegistrationContext {
        /** @var string[] */
        public array $registered = [];

        public function registerCapability(string $capability): void
        {
            $this->registered[] = $capability;
        }
    };
    $app = new Application();
    $app->register($registration);
    if (!in_array(Capabilities::class, $registration->registered, true)) {
        $fail('register() hat Capabilities::class nicht registriert.');
    }

    echo "OK: FlutCloud-Capability-Vertrag erfüllt (APP_ID, Payload-Struktur, Registrierung).\n";
}
