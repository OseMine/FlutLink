<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Exception;

/**
 * Thrown when a link/part name does not pass the NameValidator rules.
 *
 * Controllers map this to a 400 Bad Request response.
 */
class InvalidNameException extends \InvalidArgumentException
{
}
