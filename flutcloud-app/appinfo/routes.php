<?php

declare(strict_types=1);

/**
 * OCS API routes of the FlutCloud app.
 *
 * All routes are prefixed by Nextcloud with /ocs/v2.php/apps/flutcloud.
 * The FlutLink client queries the OCS capabilities endpoint
 * (/ocs/v2.php/cloud/capabilities?format=json) and refuses to connect unless
 * the server advertises the `flutcloud` capability.
 *
 * Mutating endpoints are CSRF-protected by default. External clients must
 * either authenticate with basic auth (app password) or send the
 * `OCS-APIRequest: true` header — the FlutLink client does both.
 *
 * The PublicShares#* endpoints marked @PublicPage are the anonymous,
 * strictly read-only guest entry to completely public shares; the remaining
 * PublicShares routes configure categories and locks (login + admin).
 */
return [
    'ocs' => [
        ['name' => 'Ping#ping', 'url' => '/api/v1/ping', 'verb' => 'GET'],
        ['name' => 'Links#index', 'url' => '/api/v1/links', 'verb' => 'GET'],
        ['name' => 'Links#create', 'url' => '/api/v1/links', 'verb' => 'POST'],
        ['name' => 'Links#destroy', 'url' => '/api/v1/links/{name}', 'verb' => 'DELETE'],
        ['name' => 'Parts#index', 'url' => '/api/v1/parts', 'verb' => 'GET'],
        ['name' => 'Parts#create', 'url' => '/api/v1/parts', 'verb' => 'POST'],
        ['name' => 'ProjectFolder#ensure', 'url' => '/api/v1/project-folder', 'verb' => 'POST'],
        // Guest access to complete public shares (read-only).
        ['name' => 'PublicShares#index', 'url' => '/api/v1/public', 'verb' => 'GET'],
        ['name' => 'PublicShares#categories', 'url' => '/api/v1/public/categories', 'verb' => 'GET'],
        ['name' => 'PublicShares#entries', 'url' => '/api/v1/public/{token}', 'verb' => 'GET'],
        // Admin: categories + per-share assignment + recursive locks.
        ['name' => 'PublicShares#setCategory', 'url' => '/api/v1/public/categories', 'verb' => 'POST'],
        ['name' => 'PublicShares#deleteCategory', 'url' => '/api/v1/public/categories/{name}', 'verb' => 'DELETE'],
        ['name' => 'PublicShares#assignCategory', 'url' => '/api/v1/public/shares/{token}/category', 'verb' => 'POST'],
        ['name' => 'PublicShares#unassignCategory', 'url' => '/api/v1/public/shares/{token}/category', 'verb' => 'DELETE'],
        ['name' => 'PublicShares#lockPath', 'url' => '/api/v1/public/shares/{token}/lock', 'verb' => 'POST'],
        ['name' => 'PublicShares#unlockPath', 'url' => '/api/v1/public/shares/{token}/lock', 'verb' => 'DELETE'],
    ],
    'routes' => [
        ['name' => 'Ios#index', 'url' => '/ios', 'verb' => 'GET'],
        ['name' => 'Ios#source', 'url' => '/ios/{source}', 'verb' => 'GET'],
        // Guest web routes for complete public shares. The prefixless
        // catch-all must stay last so concrete app routes keep precedence;
        // it 404s everything that is not a configured prefixless category.
        ['name' => 'PublicPages#index', 'url' => '/public', 'verb' => 'GET'],
        ['name' => 'PublicPages#category', 'url' => '/public/{category}', 'verb' => 'GET'],
        ['name' => 'PublicPages#prefixless', 'url' => '/{category}', 'verb' => 'GET'],
    ],
];
