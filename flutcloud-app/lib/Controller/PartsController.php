<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Service\LinkService;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\IRequest;

/**
 * Writable parts API (`parts/` subfolders).
 */
final class PartsController extends OcsControllerBase
{
    private LinkService $linkService;

    public function __construct(string $appName, IRequest $request, LinkService $linkService, ?string $userId)
    {
        parent::__construct($appName, $request, $userId);
        $this->linkService = $linkService;
    }

    /**
     * @NoAdminRequired
     */
    public function index(): DataResponse
    {
        return $this->run(fn (): array => $this->linkService->listParts($this->requireUser()));
    }

    /**
     * @NoAdminRequired
     */
    public function create(string $name): DataResponse
    {
        return $this->run(
            fn (): array => $this->linkService->createPart($this->requireUser(), $name),
            Http::STATUS_CREATED
        );
    }
}
