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
    ],
    'routes' => [
        ['name' => 'Ios#index', 'url' => '/ios', 'verb' => 'GET'],
        ['name' => 'Ios#source', 'url' => '/ios/{source}', 'verb' => 'GET'],
    ],
];
