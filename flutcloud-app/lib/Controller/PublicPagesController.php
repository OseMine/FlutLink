<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\AppInfo\Application;
use OCA\FlutCloud\Service\PublicShareService;
use OCP\AppFramework\Controller;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\NotFoundResponse;
use OCP\AppFramework\Http\TemplateResponse;
use OCP\IRequest;
use OCP\IURLGenerator;
use OCP\IUserSession;
use OCP\IGroupManager;

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
    private IUserSession $userSession;
    private IGroupManager $groupManager;

    public function __construct(
        string $appName,
        IRequest $request,
        PublicShareService $service,
        IURLGenerator $urlGenerator,
        IUserSession $userSession,
        IGroupManager $groupManager
    ) {
        parent::__construct($appName, $request);
        $this->service = $service;
        $this->urlGenerator = $urlGenerator;
        $this->userSession = $userSession;
        $this->groupManager = $groupManager;
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
        $allCategories = $this->service->getCategories();
        // Only show public-visibility categories in the chip navigation.
        // Link-only categories are still accessible via direct URL but
        // should not appear in the bundled listing.
        $categories = array_values(array_filter(
            $allCategories,
            static fn (array $cat): bool => ($cat['visibility'] ?? 'public') === 'public'
        ));

        if ($category !== null) {
            // Specific category page: show all its shares regardless of
            // visibility (link-only categories work via direct URL).
            $allShares = $this->service->listAllCompletePublicShares();
            $shares = array_values(array_filter(
                $allShares,
                static fn (array $share): bool => $share['category'] === $category
            ));
        } else {
            // "All" view: only public-visibility shares.
            $shares = array_values($this->service->listCompletePublicShares());
        }

        // Base of the guest web routes ("/…/public"); chips append the
        // category name only — appending a route URL would double the
        // "/public" segment.
        $publicBase = $this->urlGenerator->getAbsoluteURL('/apps/' . Application::APP_ID . '/public');
        $adminUrl = $this->urlGenerator->getAbsoluteURL('/settings/admin/' . Application::APP_ID);
        $isAdmin = false;
        $user = $this->userSession->getUser();
        if ($user !== null) {
            $isAdmin = $this->groupManager->isAdmin($user->getUID());
        }

        $params = [
            'category'   => $category,
            'categories' => $categories,
            'shares'     => $shares,
            'publicBase' => $publicBase,
            'adminUrl'   => $adminUrl,
            'isAdmin'    => $isAdmin,
        ];

        // RENDER_AS_BLANK serves the template verbatim — no Nextcloud
        // chrome, since guests are anonymous and get a standalone page.
        return new TemplateResponse('flutcloud', 'public', $params, TemplateResponse::RENDER_AS_BLANK);
    }
}
