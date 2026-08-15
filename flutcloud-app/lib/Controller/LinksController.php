<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Service\LinkService;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\IRequest;

/**
 * Virtual links API (`resources/` subfolders).
 */
final class LinksController extends OcsControllerBase
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
        return $this->run(fn (): array => $this->linkService->listLinks($this->requireUser()));
    }

    /**
     * @NoAdminRequired
     */
    public function create(string $name): DataResponse
    {
        return $this->run(
            fn (): array => $this->linkService->createLink($this->requireUser(), $name),
            Http::STATUS_CREATED
        );
    }

    /**
     * @NoAdminRequired
     */
    public function destroy(string $name): DataResponse
    {
        return $this->run(function () use ($name): array {
            $this->linkService->deleteLink($this->requireUser(), $name);
            return [];
        });
    }
}
