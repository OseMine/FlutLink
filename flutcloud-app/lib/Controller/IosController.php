<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Service\AltStoreSourceService;
use OCP\AppFramework\Controller;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\AppFramework\Http\RedirectResponse;
use OCP\IRequest;

/**
 * Public AltStore source endpoints for iOS distribution.
 *
 * GET /apps/flutcloud/ios          — list of available sources
 * GET /apps/flutcloud/ios/pal      — redirect to the latest AltStore PAL source JSON
 * GET /apps/flutcloud/ios/classic  — redirect to the latest AltStore Classic source JSON
 *
 * The source JSONs themselves live on GitHub as assets of the latest
 * FlutLink release; these endpoints make sure the URLs always resolve to the
 * newest version. They are public so AltStore can add them directly as
 * sources without Nextcloud credentials.
 */
final class IosController extends Controller
{
    private AltStoreSourceService $sources;

    public function __construct(string $appName, IRequest $request, AltStoreSourceService $sources)
    {
        parent::__construct($appName, $request);
        $this->sources = $sources;
    }

    /**
     * @NoCSRFRequired
     * @PublicPage
     */
    public function index(): DataResponse
    {
        $urls = [];
        foreach (AltStoreSourceService::SOURCES as $name) {
            $urls[$name] = $this->sources->sourceUrl($name);
        }
        return new DataResponse(['sources' => $urls]);
    }

    /**
     * @NoCSRFRequired
     * @PublicPage
     */
    public function source(string $source): RedirectResponse|DataResponse
    {
        if (!$this->sources->isKnownSource($source)) {
            return new DataResponse(
                ['message' => sprintf(
                    'Unknown AltStore source "%s"; available: %s',
                    $source,
                    implode(', ', AltStoreSourceService::SOURCES)
                )],
                Http::STATUS_NOT_FOUND
            );
        }
        return new RedirectResponse($this->sources->sourceUrl($source), Http::STATUS_FOUND);
    }
}
