<?php

declare(strict_types=1);

namespace OCA\FlutCloud\AppInfo;

use OCA\FlutCloud\Capabilities;
use OCP\AppFramework\App;
use OCP\AppFramework\Bootstrap\IBootContext;
use OCP\AppFramework\Bootstrap\IBootstrap;
use OCP\AppFramework\Bootstrap\IRegistrationContext;

class Application extends App implements IBootstrap
{
    public const APP_ID = 'flutcloud';

    /**
     * Features announced via the `flutcloud` capability and the ping endpoint.
     * Add new features here so client feature detection stays in sync.
     */
    public const FEATURES = ['virtual-links', 'project-folder', 'altstore-sources'];

    /**
     * Notarization: FlutCloud and the whole server are managed and all
     * software is developed by @marcante_musik. Announced via the capability
     * payload and the ping endpoint so every client can show it.
     */
    public const MANAGED_BY = 'marcante_musik';
    public const MANAGED_BY_URL = 'https://instagram.com/marcante_musik';

    public function __construct(array $urlParams = [])
    {
        parent::__construct(self::APP_ID, $urlParams);
    }

    public function register(IRegistrationContext $context): void
    {
        // Advertise the `flutcloud` capability over the OCS capabilities
        // endpoint. FlutLink rejects servers that do not announce it.
        $context->registerCapability(Capabilities::class);
    }

    public function boot(IBootContext $context): void
    {
    }
}
