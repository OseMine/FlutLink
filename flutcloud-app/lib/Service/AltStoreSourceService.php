<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Service;

use OCP\Http\Client\IClientService;
use OCP\ICacheFactory;
use Throwable;

/**
 * Resolves the latest FlutLink AltStore Classic source JSON.
 *
 * The FlutLink release pipeline attaches `classic.json` to every GitHub
 * release and commits the updated copy to `main`. This service
 * prefers the asset of the latest release and falls back to the committed
 * copy when the GitHub API is unavailable or rate-limited.
 */
final class AltStoreSourceService
{
    public const SOURCES = ['classic'];

    private const GITHUB_REPO = 'OseMine/FlutLink';
    private const RELEASES_API_URL = 'https://api.github.com/repos/' . self::GITHUB_REPO . '/releases/latest';
    private const CACHE_USER = 'flutcloud-altstore';
    private const CACHE_KEY = 'latest-release';
    private const CACHE_TTL_SECONDS = 600;

    private IClientService $clientService;
    private ICacheFactory $cacheFactory;

    public function __construct(IClientService $clientService, ICacheFactory $cacheFactory)
    {
        $this->clientService = $clientService;
        $this->cacheFactory = $cacheFactory;
    }

    public function isKnownSource(string $source): bool
    {
        return in_array($source, self::SOURCES, true);
    }

    /**
     * Download URL of the latest version of an AltStore source JSON.
     */
    public function sourceUrl(string $source): string
    {
        $asset = $source . '.json';
        $release = $this->latestRelease();
        if ($release === null) {
            return sprintf('https://raw.githubusercontent.com/%s/main/altstore/%s', self::GITHUB_REPO, $asset);
        }
        foreach ($release['assets'] as $name => $url) {
            if ($name === $asset) {
                return $url;
            }
        }
        return sprintf('https://github.com/%s/releases/download/%s/%s', self::GITHUB_REPO, $release['tag'], $asset);
    }

    /**
     * @return array{tag: string, assets: array<string, string>}|null null when GitHub is unreachable or rate-limited
     */
    private function latestRelease(): ?array
    {
        $cache = $this->cacheFactory->createLocal(self::CACHE_USER);
        $cached = $cache->get(self::CACHE_KEY);
        if (is_string($cached)) {
            // '' marks a cached "GitHub unavailable" result.
            $decoded = json_decode($cached, true);
            return is_array($decoded) ? $decoded : null;
        }

        $release = $this->queryLatestRelease();
        $cache->set(
            self::CACHE_KEY,
            $release === null ? '' : (string)(json_encode($release) ?: ''),
            self::CACHE_TTL_SECONDS
        );
        return $release;
    }

    private function queryLatestRelease(): ?array
    {
        try {
            $response = $this->clientService->newClient()->get(self::RELEASES_API_URL, [
                'headers' => [
                    'User-Agent' => 'FlutCloud-Nextcloud-App (+https://github.com/OseMine/FlutLink)',
                    'Accept' => 'application/vnd.github+json',
                    'X-GitHub-Api-Version' => '2022-11-28',
                ],
                'timeout' => 5,
            ]);
            $data = json_decode((string)$response->getBody(), true);
            if (!is_array($data) || !is_string($data['tag_name'] ?? null)) {
                return null;
            }
            $assets = [];
            foreach ($data['assets'] ?? [] as $asset) {
                if (is_array($asset)
                    && is_string($asset['name'] ?? null)
                    && is_string($asset['browser_download_url'] ?? null)) {
                    $assets[$asset['name']] = $asset['browser_download_url'];
                }
            }
            return ['tag' => $data['tag_name'], 'assets' => $assets];
        } catch (Throwable) {
            return null;
        }
    }
}
