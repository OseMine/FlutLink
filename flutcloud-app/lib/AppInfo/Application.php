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
