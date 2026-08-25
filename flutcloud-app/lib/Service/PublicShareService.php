<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Service;

use OCA\FlutCloud\AppInfo\Application;
use OCA\FlutCloud\Exception\InvalidNameException;
use OCP\Constants;
use OCP\Files\Folder;
use OCP\Files\NotFoundException;
use OCP\IConfig;
use OCP\IURLGenerator;
use OCP\IUserManager;
use OCP\Share\IManager as IShareManager;
use OCP\Share\IShare;

/**
 * Complete public shares ("Gast-Zugriff").
 *
 * A folder counts as *completely public* when its owner granted a link share
 * (share type LINK) without a password on it. Guests get anonymous, strictly
 * read-only access through the `/api/v1/public*` OCS endpoints and the
 * `/public[/<category>]` web routes — no account required.
 *
 * Every request resolves the share live, so folders that were taken out of
 * the complete publicity (share deleted, password set, expired) disappear
 * immediately. Locked subfolders are excluded recursively; guests receive a
 * 404 for them (no information leak), including for direct path access.
 *
 * Categories group shares (`/public/<category>`); admins can drop the
 * `/public/` prefix per category so `/<category>` works instead.
 */
class PublicShareService
{
    private const CONFIG_CATEGORIES = 'public_categories';
    private const CONFIG_ASSIGNMENTS = 'public_share_assignments';
    private const CONFIG_LOCKS_PREFIX = 'public_locks.';

    private IShareManager $shareManager;
    private IConfig $config;
    private IUserManager $userManager;
    private IURLGenerator $urlGenerator;

    public function __construct(
        IShareManager $shareManager,
        IConfig $config,
        IUserManager $userManager,
        IURLGenerator $urlGenerator
    ) {
        $this->shareManager = $shareManager;
        $this->config = $config;
        $this->userManager = $userManager;
        $this->urlGenerator = $urlGenerator;
    }

    // ---------------------------------------------------------------------
    // Guest reads (anonymous)
    // ---------------------------------------------------------------------

    /**
     * Every completely public shared folder at one place ("alle an einem Ort").
     *
     * @return array<int, array<string, mixed>>
     */
    public function listCompletePublicShares(): array
    {
        $categories = $this->getCategories();
        $assignments = $this->getAssignments();
        // One folder can carry several public link shares (several tokens).
        // Guests must see the folder once: dedupe by node id and keep the
        // token that carries a category assignment so grouping survives.
        $byNode = [];
        $result = [];
        foreach ($this->shareManager->getAllShares() as $share) {
            $entry = $this->describeCompletePublicShare($share, $categories);
            if ($entry === null) {
                continue;
            }
            $nodeId = $share->getNodeId();
            if (!isset($byNode[$nodeId])) {
                $byNode[$nodeId] = count($result);
                $result[] = $entry;
                continue;
            }
            $existing = $result[$byNode[$nodeId]];
            if (($assignments[$entry['token']] ?? null) !== null
                && ($assignments[$existing['token']] ?? null) === null) {
                $result[$byNode[$nodeId]] = $entry;
            }
        }
        return $result;
    }

    /**
     * List a folder inside a public share. Locked or missing paths throw a
     * NotFoundException so guests always see a 404.
     *
     * @return array<string, mixed>
     * @throws InvalidNameException on malformed paths
     * @throws NotFoundException when the share, path is missing or locked
     */
    public function listEntries(string $token, string $path): array
    {
        [$root] = $this->resolveShareRoot($token);
        $rel = $this->normalizeRelPath($path);
        $folder = $this->resolveFolder($token, $root, $rel);

        $entries = [];
        foreach ($folder->getDirectoryListing() as $child) {
            $childRel = rtrim($rel, '/') . '/' . $child->getName();
            if ($this->isLocked($token, $childRel)) {
                continue;
            }
            $entries[] = [
                'name' => $child->getName(),
                'path' => $childRel,
                'isDir' => $child instanceof Folder,
                'size' => $child->getSize(),
                'mtime' => $child->getMTime(),
                'contentType' => $child->getMimetype(),
            ];
        }

        return [
            'token' => $token,
            'name' => $root->getName(),
            'path' => $rel,
            'entries' => $entries,
        ];
    }

    // ---------------------------------------------------------------------
    // Categories (admin-managed, guest-readable)
    // ---------------------------------------------------------------------

    /**
     * All configured categories.
     *
     * @return array<string, array{name: string, prefixless: bool}>
     */
    public function getCategories(): array
    {
        return $this->readJson(self::CONFIG_CATEGORIES, []);
    }

    /**
     * Create or update a category.
     *
     * @throws InvalidNameException on invalid names
     */
    public function setCategory(string $name, bool $prefixless): void
    {
        $name = trim($name);
        if ($name === '') {
            throw new InvalidNameException('category must not be empty');
        }
        $categories = $this->getCategories();
        $categories[$name] = ['name' => $name, 'prefixless' => $prefixless];
        $this->writeJson(self::CONFIG_CATEGORIES, $categories);
    }

    /**
     * Delete a category and clear its share assignments.
     */
    public function deleteCategory(string $name): void
    {
        $categories = $this->getCategories();
        unset($categories[$name]);
        $this->writeJson(self::CONFIG_CATEGORIES, $categories);

        $assignments = array_filter(
            $this->getAssignments(),
            static fn (string $assigned): bool => $assigned !== $name
        );
        $this->writeJson(self::CONFIG_ASSIGNMENTS, $assignments);
    }

    /**
     * Assign a complete public share to a category.
     *
     * @throws InvalidNameException when the category does not exist
     */
    public function assignShare(string $token, string $category): void
    {
        if (!isset($this->getCategories()[$category])) {
            throw new InvalidNameException("unknown category '$category'");
        }
        if ($this->describeCompletePublicShareByToken($token) === null) {
            throw new InvalidNameException("'$token' is not a complete public share");
        }
        $assignments = $this->getAssignments();
        $assignments[$token] = $category;
        $this->writeJson(self::CONFIG_ASSIGNMENTS, $assignments);
    }

    /**
     * Remove the category assignment of a share.
     */
    public function unassignShare(string $token): void
    {
        $assignments = $this->getAssignments();
        unset($assignments[$token]);
        $this->writeJson(self::CONFIG_ASSIGNMENTS, $assignments);
    }

    /**
     * Category of a share or `null` when uncategorized.
     */
    public function getShareCategory(string $token): ?string
    {
        return $this->getAssignments()[$token] ?? null;
    }

    /**
     * Share token → category name. Orphaned entries (share gone, unknown
     * category) are ignored on read.
     *
     * @return array<string, string>
     */
    private function getAssignments(): array
    {
        /** @var array<string, string> $assignments */
        $assignments = $this->readJson(self::CONFIG_ASSIGNMENTS, []);
        return $assignments;
    }

    // ---------------------------------------------------------------------
    // Locks (admin-managed)
    // ---------------------------------------------------------------------

    /**
     * Lock a subfolder of a share (recursive: everything below disappears
     * from guest view).
     *
     * @throws InvalidNameException on malformed paths / locking the root
     */
    public function lock(string $token, string $path): void
    {
        $rel = $this->normalizeRelPath($path);
        if ($rel === '/') {
            throw new InvalidNameException('cannot lock the share root');
        }
        $locks = $this->getLocks($token);
        if (!in_array($rel, $locks, true)) {
            $locks[] = $rel;
            sort($locks);
            $this->writeJson(self::CONFIG_LOCKS_PREFIX . $token, $locks);
        }
    }

    /**
     * Remove a lock again.
     */
    public function unlock(string $token, string $path): void
    {
        $rel = $this->normalizeRelPath($path);
        $locks = array_values(array_filter(
            $this->getLocks($token),
            static fn (string $locked): bool => $locked !== $rel
        ));
        $this->writeJson(self::CONFIG_LOCKS_PREFIX . $token, $locks);
    }

    /**
     * @return string[]
     */
    public function getLocks(string $token): array
    {
        return $this->readJson(self::CONFIG_LOCKS_PREFIX . $token, []);
    }

    /**
     * True when `$relPath` is at or below any locked path of the share —
     * the lock applies recursively.
     */
    public function isLocked(string $token, string $relPath): bool
    {
        $needle = trim($relPath, '/');
        if ($needle === '') {
            return false;
        }
        foreach ($this->getLocks($token) as $locked) {
            $prefix = trim($locked, '/');
            if ($prefix === '') {
                continue;
            }
            if ($needle === $prefix || str_starts_with($needle . '/', $prefix . '/')) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Validate the token and resolve the live link share + its folder node.
     * Anything that is not a still-valid, password-free link share on a
     * folder throws NotFoundException (guests never learn why).
     *
     * @return array{0: Folder, 1: IShare}
     * @throws NotFoundException
     */
    private function resolveShareRoot(string $token): array
    {
        try {
            $share = $this->shareManager->getShareByToken($token);
        } catch (\OCP\Share\Exceptions\ShareNotFound $e) {
            throw new NotFoundException('share not found');
        }
        $node = $this->completePublicFolder($share);
        if ($node === null) {
            throw new NotFoundException('share not found');
        }
        return [$node, $share];
    }

    /**
     * Walk into the share folder segment by segment, rejecting locked or
     * missing targets with a uniform 404.
     *
     * @throws NotFoundException
     */
    private function resolveFolder(string $token, Folder $root, string $rel): Folder
    {
        if ($this->isLocked($token, $rel)) {
            throw new NotFoundException('path not found');
        }
        $folder = $root;
        if ($rel !== '/') {
            foreach (explode('/', trim($rel, '/')) as $segment) {
                try {
                    $folder = $folder->get($segment);
                } catch (NotFoundException $e) {
                    throw new NotFoundException('path not found');
                }
            }
        }
        if (!$folder instanceof Folder) {
            throw new NotFoundException('path not found');
        }
        return $folder;
    }

    /**
     * Describe a share for the guest listing when it qualifies as a complete
     * public share, else `null`.
     *
     * @param array<string, array{name: string, prefixless: bool}> $categories
     * @return array<string, mixed>|null
     */
    private function describeCompletePublicShare(IShare $share, array $categories): ?array
    {
        $folder = $this->completePublicFolder($share);
        if ($folder === null) {
            return null;
        }
        $token = $share->getToken();
        $category = $this->getShareCategory($token);
        return [
            'token' => $token,
            'name' => $folder->getName(),
            'owner' => $share->getSharedBy(),
            'ownerDisplay' => $this->userManager->getDisplayName($share->getSharedBy())
                ?? $share->getSharedBy(),
            'category' => isset($categories[$category ?? '']) ? $category : null,
            'url' => $this->urlGenerator->getAbsoluteURL('/s/' . $token),
            'downloadBase' => rtrim(
                $this->urlGenerator->getAbsoluteURL('/public.php/webdav/' . $token),
                '/'
            ),
            'mtime' => $folder->getMTime(),
        ];
    }

    /**
     * Same check as [describeCompletePublicShare] but token-driven (admin ops).
     */
    private function describeCompletePublicShareByToken(string $token): ?array
    {
        try {
            $share = $this->shareManager->getShareByToken($token);
        } catch (\OCP\Share\Exceptions\ShareNotFound $e) {
            return null;
        }
        return $this->describeCompletePublicShare($share, $this->getCategories());
    }

    /**
     * The shared node when the share is a valid, non-expired link share
     * without a password on a folder (read permission implied).
     */
    private function completePublicFolder(IShare $share): ?Folder
    {
        if ($share->getShareType() !== IShare::TYPE_LINK) {
            return null;
        }
        $password = $share->getPassword();
        if ($password !== null && $password !== '') {
            return null;
        }
        $expiration = $share->getExpirationDate();
        if ($expiration !== null && $expiration <= new \DateTime()) {
            return null;
        }
        if (($share->getPermissions() & Constants::PERMISSION_READ) === 0) {
            return null;
        }
        try {
            $node = $share->getNode();
        } catch (\OCP\Files\NotFoundException $e) {
            return null;
        } catch (\OCP\Share\Exceptions\ShareNotFound $e) {
            return null;
        }
        return $node instanceof Folder ? $node : null;
    }

    /**
     * Normalize an arbitrary client path to a canonical absolute rel path.
     *
     * @throws InvalidNameException on `..` segments
     */
    private function normalizeRelPath(string $path): string
    {
        $segments = [];
        foreach (explode('/', trim($path)) as $segment) {
            if ($segment === '' || $segment === '.') {
                continue;
            }
            if ($segment === '..') {
                throw new InvalidNameException('path must not contain ".."');
            }
            $segments[] = $segment;
        }
        return '/' . implode('/', $segments);
    }

    /**
     * @template T
     * @param T $default
     * @return T
     */
    private function readJson(string $key, $default)
    {
        $raw = $this->config->getAppValue(Application::APP_ID, $key, '');
        if ($raw === '') {
            return $default;
        }
        $decoded = json_decode($raw, true);
        return is_array($decoded) ? $decoded : $default;
    }

    /**
     * @param array<string, mixed> $value
     */
    private function writeJson(string $key, array $value): void
    {
        $this->config->setAppValue(
            Application::APP_ID,
            $key,
            json_encode($value, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR)
        );
    }
}
