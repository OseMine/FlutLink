<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Settings;

use OCP\Settings\ISection;

/**
 * "Verwaltung" sidebar entry: Einstellungen → Verwaltung → FlutCloud
 * (/settings/admin/flutcloud).
 */
class AdminSection implements ISection
{
    public function getID(): string
    {
        return 'flutcloud';
    }

    public function getName(): string
    {
        return 'FlutCloud';
    }

    public function getPriority(): int
    {
        return 75;
    }
}
