<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\AppInfo\Application;
use OCP\App\IAppManager;
use OCP\AppFramework\Http\DataResponse;
use OCP\IRequest;

/**
 * App info used by the FlutLink client to verify the server.
 */
final class PingController extends OcsControllerBase
{
    private IAppManager $appManager;

    public function __construct(string $appName, IRequest $request, IAppManager $appManager, ?string $userId)
    {
        parent::__construct($appName, $request, $userId);
        $this->appManager = $appManager;
    }

    /**
     * @NoAdminRequired
     * @NoCSRFRequired
     */
    public function ping(): DataResponse
    {
        return new DataResponse([
            'app' => Application::APP_ID,
            'name' => 'FlutCloud',
            'version' => $this->appManager->getAppVersion(Application::APP_ID),
            'features' => Application::FEATURES,
            'user' => $this->userId,
            'managed_by' => Application::MANAGED_BY,
            'managed_by_url' => Application::MANAGED_BY_URL,
        ]);
    }
}
