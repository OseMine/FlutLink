<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Service\PublicShareService;
use OCP\AppFramework\Controller;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\NotFoundResponse;
use OCP\AppFramework\Http\TemplateResponse;
use OCP\IRequest;
use OCP\IURLGenerator;

/**
 * Web (non-OCS) guest routes for completely public shares:
 *
 * - `/apps/flutcloud/public`                → every complete public share
 * - `/apps/flutcloud/public/{category}`     → shares of one category
 * - `/apps/flutcloud/{category}`            → same, for categories where the
 *                                             admin dropped the `/public/` prefix
 *
 * All endpoints are anonymous and read-only; unknown categories answer 404.
 * Returns an HTML template for browser guests.
 */
class PublicPagesController extends Controller
{
    private PublicShareService $service;
    private IURLGenerator $urlGenerator;

    public function __construct(
        string $appName,
        IRequest $request,
        PublicShareService $service,
        IURLGenerator $urlGenerator
    ) {
        parent::__construct($appName, $request);
        $this->service = $service;
        $this->urlGenerator = $urlGenerator;
    }

    /**
     * @PublicPage
     * @NoCSRFRequired
     */
    public function index(): TemplateResponse
    {
        return $this->renderPage(null);
    }

    /**
     * @PublicPage
     * @NoCSRFRequired
     */
    public function category(string $category): TemplateResponse|NotFoundResponse
    {
        if (!isset($this->service->getCategories()[$category])) {
            return new NotFoundResponse();
        }
        return $this->renderPage($category);
    }

    /**
     * Prefixless variant: only answers when the admin removed the
     * `/public/` prefix for this category.
     *
     * @PublicPage
     * @NoCSRFRequired
     */
    public function prefixless(string $category): TemplateResponse|NotFoundResponse
    {
        $config = $this->service->getCategories()[$category] ?? null;
        if ($config === null || !$config['prefixless']) {
            return new NotFoundResponse();
        }
        return $this->renderPage($category);
    }

    private function renderPage(?string $category): TemplateResponse
    {
        $categories = array_values($this->service->getCategories());
        $shares = array_values(array_filter(
            $this->service->listCompletePublicShares(),
            static fn (array $share): bool => $category === null || $share['category'] === $category
        ));

        $params = [
            'category'  => $category,
            'categories' => $categories,
            'shares'    => $shares,
            'appUrl'    => $this->urlGenerator->linkToRoute('flutcloud.publicPages.index'),
        ];

        return new TemplateResponse('flutcloud', 'public', $params);
    }
}
