<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Exception\InvalidNameException;
use OCA\FlutCloud\Service\PublicShareService;
use OCP\AppFramework\Http\DataResponse;
use OCP\AppFramework\OCS\OCSForbiddenException;
use OCP\AppFramework\OCS\OCSNotFoundException;
use OCP\Files\NotFoundException;
use OCP\IGroupManager;
use OCP\IRequest;

/**
 * Guest access to completely public shares plus the admin configuration of
 * categories and locks.
 *
 * The `index`/`categories`/`entries` endpoints are anonymous (@PublicPage)
 * and strictly read-only: there is no write path at all, so guests can only
 * browse and download. Admin endpoints require login + admin rights.
 *
 * Download URLs point at the standard Nextcloud public WebDAV endpoint
 * (`/public.php/webdav/<token>/…`, basic auth with the token as username),
 * which enforces read-only on the server for read-only links.
 */
class PublicSharesController extends OcsControllerBase
{
    private PublicShareService $service;
    private IGroupManager $groupManager;

    public function __construct(
        string $appName,
        IRequest $request,
        PublicShareService $service,
        IGroupManager $groupManager,
        ?string $userId
    ) {
        parent::__construct($appName, $request, $userId);
        $this->service = $service;
        $this->groupManager = $groupManager;
    }

    // ---------------------------------------------------------------------
    // Guest (anonymous, read-only)
    // ---------------------------------------------------------------------

    /**
     * Bundled guest view: every completely public shared folder at one place.
     *
     * @PublicPage
     * @NoCSRFRequired
     * @NoAdminRequired
     */
    public function index(): DataResponse
    {
        return new DataResponse([
            'shares' => $this->service->listCompletePublicShares(),
            'categories' => array_values($this->service->getCategories()),
        ]);
    }

    /**
     * Categories available to guests.
     *
     * @PublicPage
     * @NoCSRFRequired
     * @NoAdminRequired
     */
    public function categories(): DataResponse
    {
        return new DataResponse([
            'categories' => array_values($this->service->getCategories()),
        ]);
    }

    /**
     * Browse into a public share (`path` query parameter, defaults to "/").
     * Locked/missing paths answer 404.
     *
     * @PublicPage
     * @NoCSRFRequired
     * @NoAdminRequired
     */
    public function entries(string $token): DataResponse
    {
        $path = (string)$this->request->getParam('path', '/');
        try {
            return new DataResponse($this->service->listEntries($token, $path));
        } catch (InvalidNameException | NotFoundException $e) {
            throw new OCSNotFoundException($e->getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Admin configuration
    // ---------------------------------------------------------------------

    /**
     * Create/update a category. Body: `name`, optional `prefixless` ("1"/"true"),
     * optional `visibility` ("public"|"link-only").
     *
     * When updating an existing category, omitted fields keep their current
     * values — the caller only needs to send what changed.
     */
    public function setCategory(): DataResponse
    {
        $this->requireAdmin();
        $name = (string)$this->request->getParam('name', '');
        $prefixless = in_array(
            strtolower((string)$this->request->getParam('prefixless', '')),
            ['1', 'true', 'yes'],
            true
        );
        $visibilityRaw = $this->request->getParam('visibility');
        // Preserve existing visibility when not provided (partial update).
        $visibility = 'public';
        if ($visibilityRaw === null) {
            $existing = $this->service->getCategories()[$name] ?? null;
            if ($existing !== null) {
                $visibility = $existing['visibility'] ?? 'public';
            }
        } else {
            $visibility = (string)$visibilityRaw;
        }
        return $this->run(function () use ($name, $prefixless, $visibility) {
            $this->service->setCategory($name, $prefixless, $visibility);
            return ['status' => 'ok'];
        });
    }

    /**
     * Delete a category.
     *
     * @throws OCSNotFoundException when the category does not exist
     */
    public function deleteCategory(string $name): DataResponse
    {
        $this->requireAdmin();
        return $this->run(function () use ($name) {
            if (!isset($this->service->getCategories()[$name])) {
                throw new OCSNotFoundException("unknown category '$name'");
            }
            $this->service->deleteCategory($name);
            return ['status' => 'ok'];
        });
    }

    /**
     * Assign a share to a category. Body: `category`.
     */
    public function assignCategory(string $token): DataResponse
    {
        $this->requireAdmin();
        $category = (string)$this->request->getParam('category', '');
        return $this->run(function () use ($token, $category) {
            $this->service->assignShare($token, $category);
            return ['status' => 'ok'];
        });
    }

    /**
     * Remove a share's category assignment.
     */
    public function unassignCategory(string $token): DataResponse
    {
        $this->requireAdmin();
        return $this->run(function () use ($token) {
            $this->service->unassignShare($token);
            return ['status' => 'ok'];
        });
    }

    /**
     * Lock a subfolder of a share (recursive). Body: `path`.
     */
    public function lockPath(string $token): DataResponse
    {
        $this->requireAdmin();
        $path = (string)$this->request->getParam('path', '');
        return $this->run(function () use ($token, $path) {
            $this->service->lock($token, $path);
            return ['status' => 'ok', 'locks' => $this->service->getLocks($token)];
        });
    }

    /**
     * Remove a lock again. Body: `path`.
     */
    public function unlockPath(string $token): DataResponse
    {
        $this->requireAdmin();
        $path = (string)$this->request->getParam('path', '');
        return $this->run(function () use ($token, $path) {
            $this->service->unlock($token, $path);
            return ['status' => 'ok', 'locks' => $this->service->getLocks($token)];
        });
    }

    /**
     * Current lock list of a share (read-only; for the web admin page).
     */
    public function locksFor(string $token): DataResponse
    {
        $this->requireAdmin();
        return new DataResponse(['locks' => $this->service->getLocks($token)]);
    }

    /**
     * @throws OCSForbiddenException when the caller is not an admin
     */
    private function requireAdmin(): void
    {
        $userId = $this->requireUser();
        if (!$this->groupManager->isAdmin($userId)) {
            throw new OCSForbiddenException('admin required');
        }
    }
}
