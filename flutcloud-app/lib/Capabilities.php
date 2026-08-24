<?php

declare(strict_types=1);

namespace OCA\FlutCloud;

use OCA\FlutCloud\AppInfo\Application;
use OCP\App\IAppManager;
use OCP\Capabilities\ICapability;

/**
 * Adds the `flutcloud` capability to the OCS capabilities payload.
 *
 * GET /ocs/v2.php/cloud/capabilities?format=json
 *   → ocs.data.capabilities.flutcloud
 */
class Capabilities implements ICapability
{
    private IAppManager $appManager;

    public function __construct(IAppManager $appManager)
    {
        $this->appManager = $appManager;
    }

    public function getCapabilities(): array
    {
        return [
            Application::APP_ID => [
                'version' => $this->appManager->getAppVersion(Application::APP_ID),
                'features' => Application::FEATURES,
                'managed_by' => Application::MANAGED_BY,
                'managed_by_url' => Application::MANAGED_BY_URL,
            ],
        ];
    }
}
