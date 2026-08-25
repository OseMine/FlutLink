<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Settings;

use OCA\FlutCloud\Service\PublicShareService;
use OCP\AppFramework\Http\TemplateResponse;
use OCP\Settings\ISettings;

/**
 * Admin settings form: public-share categories, share assignment and
 * subfolder locks — the web counterpart of the desktop/mobile admin UI.
 * All mutations go through the existing OCS admin endpoints.
 */
class FlutCloudAdmin implements ISettings
{
    private PublicShareService $service;

    public function __construct(PublicShareService $service)
    {
        $this->service = $service;
    }

    public function getForm(): TemplateResponse
    {
        return new TemplateResponse('flutcloud', 'admin-settings', [
            'categories' => array_values($this->service->getCategories()),
            'shares'     => $this->service->listCompletePublicShares(),
        ]);
    }

    public function getSection(): string
    {
        return 'flutcloud';
    }

    public function getPriority(): int
    {
        return 50;
    }
}
