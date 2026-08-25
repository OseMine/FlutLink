<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Dav;

use OCA\FlutCloud\Service\PublicShareService;
use Sabre\DAV\Server;
use Sabre\DAV\ServerPlugin;
use Sabre\HTTP\RequestInterface;

/**
 * Enforces guest subfolder locks at the WebDAV layer.
 *
 * Guests download files through Nextcloud's public WebDAV endpoint
 * (`/public.php/webdav/<token>/<path>`). Without this plugin a guest could
 * bypass the FlutCloud lock list by talking to that endpoint directly. Any
 * request whose target sits at or below a locked path answers 404 — same
 * rule as the app's own guest API (see [PublicShareService::isLocked]).
 */
final class GuestLockPlugin extends ServerPlugin
{
    private const PUBLIC_DAV_SUFFIX = '/public.php/webdav/';

    private PublicShareService $service;

    public function __construct(PublicShareService $service)
    {
        $this->service = $service;
    }

    public function initialize(Server $server): void
    {
        $server->on('beforeMethod', [$this, 'beforeMethod'], 100);
    }

    /**
     * @throws \Sabre\DAV\Exception\NotFound when the target is locked
     */
    public function beforeMethod(RequestInterface $request): bool
    {
        [$token, $rel] = $this->splitPublicDavPath((string)$request->getPath());
        if ($token === null) {
            return true;
        }
        if ($this->service->isLocked($token, $rel)) {
            throw new \Sabre\DAV\Exception\NotFound('Not found');
        }
        return true;
    }

    /**
     * Split `/…/public.php/webdav/<token>[/<path>]` into token + rel path.
     * Returns `[null, null]` for every non-public DAV URL.
     *
     * @return array{0: string|null, 1: string}
     */
    private function splitPublicDavPath(string $path): array
    {
        $marker = strpos($path, self::PUBLIC_DAV_SUFFIX);
        if ($marker === false) {
            return [null, ''];
        }
        $rest = trim(substr($path, $marker + strlen(self::PUBLIC_DAV_SUFFIX)), '/');
        if ($rest === '') {
            return [null, ''];
        }
        $slash = strpos($rest, '/');
        if ($slash === false) {
            return [$rest, '/'];
        }
        return [substr($rest, 0, $slash), substr($rest, $slash)];
    }
}
