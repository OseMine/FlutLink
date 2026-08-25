<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Service\PublicShareService;
use OCP\AppFramework\Controller;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\JSONResponse;
use OCP\IRequest;

/**
 * Web (non-OCS) guest routes for completely public shares:
 *
 * - `/apps/flutcloud/public`                → every complete public share
 * - `/apps/flutcloud/public/{category}`     → shares of one category
 * - `/apps/flutcloud/{category}`            → same, for categories where the
 *                                             admin dropped the `/public/` prefix
 *
 * All endpoints are anonymous and read-only; unknown categories answer 404.
 */
class PublicPagesController extends Controller
{
    private PublicShareService $service;

    public function __construct(string $appName, IRequest $request, PublicShareService $service)
    {
        parent::__construct($appName, $request);
        $this->service = $service;
    }

    /**
     * @PublicPage
     * @NoCSRFRequired
     */
    public function index(): JSONResponse
    {
        return new JSONResponse($this->payload(null));
    }

    /**
     * @PublicPage
     * @NoCSRFRequired
     */
    public function category(string $category): JSONResponse
    {
        if (!isset($this->service->getCategories()[$category])) {
            return new JSONResponse(['message' => "unknown category '$category'"], Http::STATUS_NOT_FOUND);
        }
        return new JSONResponse($this->payload($category));
    }

    /**
     * Prefixless variant: only answers when the admin removed the
     * `/public/` prefix for this category.
     *
     * @PublicPage
     * @NoCSRFRequired
     */
    public function prefixless(string $category): JSONResponse
    {
        $config = $this->service->getCategories()[$category] ?? null;
        if ($config === null || !$config['prefixless']) {
            return new JSONResponse(['message' => 'not found'], Http::STATUS_NOT_FOUND);
        }
        return new JSONResponse($this->payload($category));
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(?string $category): array
    {
        $categories = array_values($this->service->getCategories());
        $shares = array_values(array_filter(
            $this->service->listCompletePublicShares(),
            static fn (array $share): bool => $category === null || $share['category'] === $category
        ));
        return ['category' => $category, 'categories' => $categories, 'shares' => $shares];
    }
}
