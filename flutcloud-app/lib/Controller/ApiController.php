<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Service\LinkService;
use OCP\App\IAppManager;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\AppFramework\OCSController;
use OCP\IGroupManager;
use OCP\IRequest;

/**
 * OCS endpoints for the non-standard FlutCloud features used by FlutLink.
 *
 * All responses are plain JSON arrays (camelCase keys) so the client can
 * parse them without extra XML handling.
 */
class ApiController extends OCSController
{
    private LinkService $linkService;
    private IGroupManager $groupManager;
    private IAppManager $appManager;
    private ?string $userId;

    public function __construct(
        string $appName,
        IRequest $request,
        LinkService $linkService,
        IGroupManager $groupManager,
        IAppManager $appManager,
        ?string $userId
    ) {
        parent::__construct($appName, $request);
        $this->linkService = $linkService;
        $this->groupManager = $groupManager;
        $this->appManager = $appManager;
        $this->userId = $userId;
    }

    /**
     * @NoAdminRequired
     * @NoCSRFRequired
     */
    public function ping(): DataResponse
    {
        return new DataResponse([
            'app' => 'flutcloud',
            'name' => 'FlutCloud',
            'version' => $this->appManager->getAppVersion('flutcloud'),
            'features' => ['virtual-links', 'project-folder'],
            'user' => $this->userId,
        ]);
    }

    /**
     * @NoAdminRequired
     */
    public function links(): DataResponse
    {
        $userId = $this->requireUser();
        return new DataResponse($this->linkService->listLinks($userId));
    }

    /**
     * @NoAdminRequired
     */
    public function createLink(string $name): DataResponse
    {
        $userId = $this->requireUser();
        if (trim($name) === '') {
            return new DataResponse(['message' => 'name must not be empty'], Http::STATUS_BAD_REQUEST);
        }
        return new DataResponse($this->linkService->createLink($userId, $name), Http::STATUS_CREATED);
    }

    /**
     * @NoAdminRequired
     */
    public function deleteLink(string $name): DataResponse
    {
        $userId = $this->requireUser();
        $this->linkService->deleteLink($userId, $name);
        return new DataResponse([], Http::STATUS_OK);
    }

    /**
     * @NoAdminRequired
     */
    public function parts(): DataResponse
    {
        $userId = $this->requireUser();
        return new DataResponse($this->linkService->listParts($userId));
    }

    /**
     * @NoAdminRequired
     */
    public function createPart(string $name): DataResponse
    {
        $userId = $this->requireUser();
        if (trim($name) === '') {
            return new DataResponse(['message' => 'name must not be empty'], Http::STATUS_BAD_REQUEST);
        }
        return new DataResponse($this->linkService->createPart($userId, $name), Http::STATUS_CREATED);
    }

    /**
     * Ensure the shared project folder. Only admins may write it.
     *
     * @NoAdminRequired
     */
    public function ensureProjectFolder(): DataResponse
    {
        if ($this->userId === null || !$this->groupManager->isAdmin($this->userId)) {
            return new DataResponse(['message' => 'admin required'], Http::STATUS_FORBIDDEN);
        }
        return new DataResponse(
            $this->linkService->ensureProjectFolder($this->userId),
            Http::STATUS_OK
        );
    }

    private function requireUser(): string
    {
        if ($this->userId === null) {
            throw new \OCP\AppFramework\OCS\OCSForbiddenException('not authenticated');
        }
        return $this->userId;
    }
}
