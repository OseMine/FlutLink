<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Controller;

use OCA\FlutCloud\Exception\InvalidNameException;
use OCA\FlutCloud\Exception\NotAFolderException;
use OCP\AppFramework\Http;
use OCP\AppFramework\Http\DataResponse;
use OCP\AppFramework\OCS\OCSForbiddenException;
use OCP\AppFramework\OCSController;
use OCP\IRequest;

/**
 * Base class for the FlutCloud OCS controllers.
 *
 * Provides the authenticated-user helper and a `run` wrapper that maps the
 * domain exceptions to proper HTTP status codes instead of leaking 500s.
 */
abstract class OcsControllerBase extends OCSController
{
    protected ?string $userId;

    public function __construct(string $appName, IRequest $request, ?string $userId)
    {
        parent::__construct($appName, $request);
        $this->userId = $userId;
    }

    /**
     * @throws OCSForbiddenException when no user is logged in
     */
    protected function requireUser(): string
    {
        if ($this->userId === null) {
            throw new OCSForbiddenException('not authenticated');
        }
        return $this->userId;
    }

    /**
     * Execute a handler and map its domain exceptions to HTTP responses.
     */
    protected function run(callable $handler, int $status = Http::STATUS_OK): DataResponse
    {
        try {
            return new DataResponse($handler(), $status);
        } catch (InvalidNameException $e) {
            return new DataResponse(['message' => $e->getMessage()], Http::STATUS_BAD_REQUEST);
        } catch (NotAFolderException $e) {
            return new DataResponse(['message' => $e->getMessage()], Http::STATUS_CONFLICT);
        }
    }
}
