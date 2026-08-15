<?php

declare(strict_types=1);

namespace OCA\FlutCloud\Service;

use OCA\FlutCloud\Exception\NotAFolderException;
use OCP\Files\Folder;
use OCP\Files\IRootFolder;

/**
 * Manages the non-standard FlutCloud storage layout inside a user's home:
 *
 * - `resources/`  read-only virtual links (each subfolder is a resource)
 * - `parts/`      writable parts that a resource can be "unpacked" into
 * - `/FlutLink/FlutCloud` shared project folder (admin home, bilingual README)
 *
 * The FlutLink client renders `resources` and `parts` as dedicated panes and
 * must be able to depend on these folders existing on a FlutCloud server.
 */
class LinkService
{
    public const RESOURCES = 'resources';
    public const PARTS = 'parts';
    public const PROJECT_PATH = '/FlutLink/FlutCloud';
    public const PROJECT_README = <<<'MD'
# FlutCloud — Nextcloud App

Shared project space of the **FlutCloud Nextcloud app**.

## Purpose
- Feature requests for the FlutCloud app and the FlutLink desktop client
- Connection notes between FlutCloud, FlutLink (desktop) and the upcoming
  FlutLink mobile app

## Feature requests
Create one folder per request, e.g. `FR-001-share-links/`, containing a note
describing: what it should do, why (use case) and the expected behaviour.

---

# FlutCloud — Nextcloud App

Gemeinsamer Projektbereich der **FlutCloud-Nextcloud-App**.

## Zweck
- Feature-Requests für die FlutCloud-App und den FlutLink-Desktop-Client
- Verbindungsnotizen zwischen FlutCloud, FlutLink (Desktop) und der geplanten
  FlutLink-Mobile-App

## Feature-Requests
Lege pro Request einen Ordner an, z. B. `FR-001-share-links/`, mit einer
Notiz, die beschreibt: was passieren soll, warum (Anwendungsfall) und das
erwartete Verhalten.
MD;

    private IRootFolder $rootFolder;
    private NameValidator $nameValidator;

    public function __construct(IRootFolder $rootFolder, NameValidator $nameValidator)
    {
        $this->rootFolder = $rootFolder;
        $this->nameValidator = $nameValidator;
    }

    /**
     * Ensure a folder exists relative to the user's home and return it.
     */
    private function ensureFolder(string $userId, string $rel): Folder
    {
        $userFolder = $this->rootFolder->getUserFolder($userId);
        $folder = $userFolder;
        foreach (array_filter(explode('/', trim($rel, '/'))) as $segment) {
            if ($folder->nodeExists($segment)) {
                $node = $folder->get($segment);
                if (!$node instanceof Folder) {
                    throw new NotAFolderException("'$segment' exists but is not a folder");
                }
                $folder = $node;
            } else {
                $folder = $folder->newFolder($segment);
            }
        }
        return $folder;
    }

    /**
     * List virtual links (= subfolders of `resources/`).
     *
     * @return array{name: string, path: string, readOnly: bool}[]
     */
    public function listLinks(string $userId): array
    {
        $root = $this->ensureFolder($userId, self::RESOURCES);
        return $this->listSubfolders($root);
    }

    /**
     * Create a virtual link folder under `resources/`. Idempotent.
     *
     * @throws \OCA\FlutCloud\Exception\InvalidNameException on invalid names
     * @throws NotAFolderException when a path segment exists as a file
     */
    public function createLink(string $userId, string $name): array
    {
        $this->nameValidator->assertValid($name);
        $folder = $this->ensureFolder($userId, self::RESOURCES . '/' . $name);
        return $this->describe($folder);
    }

    /**
     * Remove a virtual link folder. No-op if it does not exist. Only folders
     * are removed; a file stored under a link name is left untouched.
     *
     * @throws \OCA\FlutCloud\Exception\InvalidNameException on invalid names
     * @throws NotAFolderException when the target exists as a file
     */
    public function deleteLink(string $userId, string $name): void
    {
        $this->nameValidator->assertValid($name);
        $userFolder = $this->rootFolder->getUserFolder($userId);
        $path = self::RESOURCES . '/' . $name;
        if (!$userFolder->nodeExists($path)) {
            return;
        }
        $node = $userFolder->get($path);
        if (!$node instanceof Folder) {
            throw new NotAFolderException("'$name' is not a link folder");
        }
        $node->delete();
    }

    /**
     * List writable parts (= subfolders of `parts/`).
     *
     * @return array{name: string, path: string, readOnly: bool}[]
     */
    public function listParts(string $userId): array
    {
        $root = $this->ensureFolder($userId, self::PARTS);
        return $this->listSubfolders($root);
    }

    /**
     * Create a writable part folder under `parts/`. Idempotent.
     *
     * @throws \OCA\FlutCloud\Exception\InvalidNameException on invalid names
     * @throws NotAFolderException when a path segment exists as a file
     */
    public function createPart(string $userId, string $name): array
    {
        $this->nameValidator->assertValid($name);
        $folder = $this->ensureFolder($userId, self::PARTS . '/' . $name);
        return $this->describe($folder);
    }

    /**
     * Ensure the shared project folder `/FlutLink/FlutCloud` plus its
     * bilingual README in the given user's home.
     */
    public function ensureProjectFolder(string $userId): array
    {
        $root = $this->ensureFolder($userId, self::PROJECT_PATH);
        if (!$root->nodeExists('README.md')) {
            $root->newFile('README.md', self::PROJECT_README);
        }
        return $this->describe($root);
    }

    /**
     * @return array{name: string, path: string, readOnly: bool}[]
     */
    private function listSubfolders(Folder $parent): array
    {
        $result = [];
        foreach ($parent->getDirectoryListing() as $node) {
            if ($node instanceof Folder) {
                $result[] = $this->describe($node);
            }
        }
        return $result;
    }

    /**
     * @return array{name: string, path: string, readOnly: bool}
     */
    private function describe(Folder $folder): array
    {
        $internal = $folder->getInternalPath();
        return [
            'name' => $folder->getName(),
            'path' => '/' . trim($internal, '/'),
            'readOnly' => $internal === self::RESOURCES
                || str_starts_with($internal, self::RESOURCES . '/'),
        ];
    }
}
