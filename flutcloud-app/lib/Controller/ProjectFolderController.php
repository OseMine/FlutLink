<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Exception\NotAFolderException;
use OCA\FlutCloud\Service\LinkService;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\IGroupManager;
use OCP\IRequest;

/**
 * Shared project folder API.
 */
final class ProjectFolderController extends OcsControllerBase
{
    private LinkService $linkService;
    private IGroupManager $groupManager;

    public function __construct(
        string $appName,
        IRequest $request,
        LinkService $linkService,
        IGroupManager $groupManager,
        ?string $userId
    ) {
        parent::__construct($appName, $request, $userId);
        $this->linkService = $linkService;
        $this->groupManager = $groupManager;
    }

    /**
     * Ensure the shared project folder. Only admins may write it.
     *
     * @NoAdminRequired
     */
    public function ensure(): DataResponse
    {
        $userId = $this->requireUser();
        if (!$this->groupManager->isAdmin($userId)) {
            return new DataResponse(['message' => 'admin required'], Http::STATUS_FORBIDDEN);
        }
        try {
            return new DataResponse($this->linkService->ensureProjectFolder($userId));
        } catch (NotAFolderException $e) {
            return new DataResponse(['message' => $e->getMessage()], Http::STATUS_CONFLICT);
        }
    }
}
