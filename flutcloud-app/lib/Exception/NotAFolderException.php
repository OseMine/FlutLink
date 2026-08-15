<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Exception;

/**
 * Thrown when a link/part path segment exists but is a file instead of a
 * folder, or when a delete targets a non-folder node.
 *
 * Controllers map this to a 409 Conflict response.
 */
class NotAFolderException extends \RuntimeException
{
}
