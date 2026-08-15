<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Service;

use OCA\FlutCloud\Exception\InvalidNameException;

/**
 * Validates link/part folder names.
 *
 * Names are rejected when they would create nested folders (path separators),
 * reference the parent directory (`.`/`..`), contain control characters, or
 * are too long. The rules apply to every entry point so a future caller cannot
 * accidentally create unsafe folder names.
 */
class NameValidator
{
    public const MAX_LENGTH = 128;

    /**
     * @throws InvalidNameException when the name is not usable as a folder name
     */
    public function assertValid(string $name): void
    {
        if (trim($name) === '') {
            throw new InvalidNameException('name must not be empty');
        }
        if ($name !== trim($name)) {
            throw new InvalidNameException('name must not start or end with whitespace');
        }
        if (strlen($name) > self::MAX_LENGTH) {
            throw new InvalidNameException('name must not exceed ' . self::MAX_LENGTH . ' characters');
        }
        if (str_contains($name, '/') || str_contains($name, '\\')) {
            throw new InvalidNameException('name must not contain "/" or "\\"');
        }
        if ($name === '.' || $name === '..') {
            throw new InvalidNameException('name must not be "." or ".."');
        }
        if (preg_match('/[\x00-\x1F\x7F]/', $name) === 1) {
            throw new InvalidNameException('name must not contain control characters');
        }
    }
}
